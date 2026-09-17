package com.apexfit.app
import androidx.room.withTransaction
import com.apexfit.app.di.ServiceLocator
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.data.*
import com.apexfit.app.utils.ProgressionEngine.OutcomeType
import com.apexfit.app.utils.toDisplayWeight
import com.apexfit.app.utils.fromDisplayWeightToKg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.UUID
import com.google.gson.Gson
import kotlin.math.roundToInt

/**
 * Owns all in-memory workout state. A single source of truth for the
 * active session from first tap to session complete.
 *
 * Nothing touches Room until commitToDatabase() is called — one atomic
 * write covers the TrainingSession row and all ExerciseSet rows together.
 */
class WorkoutSessionManager(
    private val repository: FitnessRepository,
    private val dataStore: DataStoreManager,
    private val scope: CoroutineScope,
    private val dao: FitnessDao,
    private val appContext: android.content.Context,
) {

    private var lastProgressionResults: MutableMap<String, Any> = mutableMapOf()
    private var metadataMap: Map<String, ExerciseMetadata> = emptyMap()
    private val gson = Gson()
    private val persistJob = MutableStateFlow<kotlinx.coroutines.Job?>(null)

    init {
        scope.launch {
            val json = dataStore.activeSessionJsonFlow.firstOrNull()
            if (!json.isNullOrEmpty()) {
                try {
                    val restored = gson.fromJson(json, ActiveSession::class.java)
                    if (restored != null) {
                        if (restored.schemaVersion != ActiveSession.CURRENT_VERSION) {
                            throw IllegalStateException(
                                "ActiveSession schema mismatch: stored v${restored.schemaVersion}, current v${ActiveSession.CURRENT_VERSION}"
                            )
                        }
                        _activeSession.value = restored
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WorkoutSessionManager", "Session restore failed — clearing corrupt JSON", e)
                    _sessionRestoreFailed.value = true
                    scope.launch {
                        try { dataStore.saveActiveSessionJson(null) } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    // ── Active session state ──────────────────────────────────────
    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession

    // Last weights loaded for each exercise — shown as placeholder text
    // Key = exerciseId, Value = last logged weight or plan default
    private val _lastWeights = MutableStateFlow<Map<String, Double>>(emptyMap())
    val lastWeights: StateFlow<Map<String, Double>> = _lastWeights

    // Suggested weight context messages — displayed under or around inputs in the UI
    // Key = exerciseId, Value = string message (e.g. "Last: 12 lbs @ RPE 8 -> Suggested: 15 lbs")
    private val _weightContextLines = MutableStateFlow<Map<String, String>>(emptyMap())
    val weightContextLines: StateFlow<Map<String, String>> = _weightContextLines

    // PR preview — set before a set is logged if it would beat the record
    private val _pendingPRWarnings = MutableStateFlow<Map<String, Double>>(emptyMap())
    val pendingPRWarnings: StateFlow<Map<String, Double>> = _pendingPRWarnings

    private val _sessionRestoreFailed = MutableStateFlow(false)
    val sessionRestoreFailed: StateFlow<Boolean> = _sessionRestoreFailed.asStateFlow()

    private val _isStartingSession = MutableStateFlow(false)
    val isStartingSession: StateFlow<Boolean> = _isStartingSession.asStateFlow()

    private val _currentRestSeconds = MutableStateFlow(0)
    val currentRestSeconds: StateFlow<Int> = _currentRestSeconds.asStateFlow()

    fun incrementRestSeconds() {
        _currentRestSeconds.value++
    }

    fun resetRestSeconds() {
        _currentRestSeconds.value = 0
    }

    fun commitRestTaken(exerciseId: String, setIndex: Int, seconds: Int) {
        val session = _activeSession.value ?: return
        val exercise = session.exercises.find { it.exerciseId == exerciseId } ?: return
        if (setIndex < exercise.sets.size) {
            val set = exercise.sets[setIndex]
            updateSet(
                exerciseId = exerciseId,
                setIndex = setIndex,
                weight = set.weight,
                reps = set.reps,
                rpe = set.rpe,
                restTakenSeconds = seconds,
                completed = set.completed,
                repsInReserve = set.repsInReserve
            )
        }
    }

    fun calculateRestTimeSeconds(exerciseId: String, exerciseType: String, rpe: Int): Int {
        return com.apexfit.app.utils.ProgressionEngine.calculateRestTimeSeconds(
            exerciseType = exerciseType,
            rpe = rpe,
            restSecondsOverride = metadataMap[exerciseId]?.defaultRestSeconds
        )
    }

    // ─────────────────────────────────────────────────────────────
    // START SESSION
    // ─────────────────────────────────────────────────────────────

    /**
     * Called when user taps a plan day to begin.
     * Calculates the starting or progressive overload suggested weights per scientific calculations.
     */
    suspend fun startSession(
        planSession: PlanSession,
        exercises: List<PlanExercise>,
        userWeight: Double,
        userHeight: Double,
        preferredUnits: String
    ) = withContext(Dispatchers.IO) {
        if (_isStartingSession.value) return@withContext
        _isStartingSession.value = true
        try {
            val lastWeightMap = mutableMapOf<String, Double>()
            val suggestionsMap = mutableMapOf<String, Double>()
            val contextLinesMap = mutableMapOf<String, String>()

            val today = com.apexfit.app.utils.DateTimeUtils.todayDateString()

        // Calculate per-muscle readiness
        val readinessScore = try {
            // Read the already-cached rich sessions — no DB round-trip needed
            val completedRichSessions = com.apexfit.app.di.ServiceLocator.richSessionsFlow.value
                .map { rs ->
                    com.apexfit.app.utils.TrainingSession(
                        date = rs.date,
                        sessionType = rs.sessionType,
                        completed = rs.completed,
                        sessionFeel = rs.sessionFeel,
                        durationMinutes = rs.durationMinutes,
                        exercises = rs.exercises.map { exercise ->
                            com.apexfit.app.utils.ExerciseLog(
                                id = exercise.id,
                                name = exercise.name,
                                muscleGroup = exercise.muscleGroup,
                                secondaryMuscles = exercise.secondaryMuscles,
                                sets = exercise.sets.map { s ->
                                    com.apexfit.app.utils.ExerciseSet(
                                        weight = s.weight,
                                        reps = s.reps,
                                        rpe = s.rpe,
                                        isWarmup = s.isWarmup,
                                        completed = s.completed
                                    )
                                }
                            )
                        }
                    )
                }

            // Parallel reads: launch all three concurrently and await together
            val nutritionLogDeferred = async { repository.getAllNutritionEntriesFlow().firstOrNull() ?: emptyList() }
            val calorieTargetDeferred = async { repository.getCalorieTargetFlow().firstOrNull() ?: com.apexfit.app.UserDefaults.CALORIES }
            val userGoalDeferred = async { repository.getGoalFlow().firstOrNull() ?: "Maintain Weight" }

            val nutritionLog = nutritionLogDeferred.await()
            val calorieTarget = calorieTargetDeferred.await()
            val userGoal = userGoalDeferred.await()
            val latestWeight = repository.getCurrentWeightFlow().firstOrNull() ?: com.apexfit.app.UserDefaults.WEIGHT_KG
            val targets = com.apexfit.app.utils.AlgorithmEngine.calcMacroTargets(calorieTarget, latestWeight, userGoal)

            val ids = exercises.map { exerciseNameToSlug(it.name) }
            metadataMap = dao.getMetadataForExercises(ids)
                .associateBy { it.exerciseId }

            com.apexfit.app.utils.ReadinessFinal.buildReadinessInputs(
                completedSessions = completedRichSessions,
                todayExercises = exercises,
                nutritionLog = nutritionLog,
                targets = targets,
                metadata = metadataMap
            )
        } catch (e: Exception) {
            null
        }

        val ids = exercises.map { exerciseNameToSlug(it.name) }
        if (metadataMap.isEmpty()) {
            metadataMap = dao.getMetadataForExercises(ids)
                .associateBy { it.exerciseId }
        }
        val lastSets = dao.getLastSetsWithDateForExercises(ids)
            .groupBy { it.exerciseId }
            .mapValues { it.value.first() }

        val allExerciseSlugs = exercises.map { exerciseNameToSlug(it.name) }
        val allHistorySets = repository.getLastSetsForExercises(allExerciseSlugs).groupBy { it.exerciseId }

        exercises.forEach { ex ->
            val exerciseUnit = ex.weightUnit.ifBlank { preferredUnits }
            val lastSet = lastSets[exerciseNameToSlug(ex.name)]
            val exType = com.apexfit.app.utils.ProgressionEngine.getExerciseType(ex.name, ex.muscleGroup)

            val suggestedPreferred: Double
            if (lastSet == null) {
                // First-time or beginner starting weight
                suggestedPreferred = if (ex.weight > 0.0) {
                    ex.weight.toDisplayWeight(exerciseUnit)
                } else {
                    com.apexfit.app.utils.ProgressionEngine.calculateBeginnerStartingWeight(
                        exerciseType = exType,
                        userBodyWeightKg = userWeight,
                        userHeightCm = userHeight,
                        preferredUnits = exerciseUnit
                    )
                }

                suggestionsMap[exerciseNameToSlug(ex.name)] = suggestedPreferred
                lastWeightMap[exerciseNameToSlug(ex.name)] = suggestedPreferred.fromDisplayWeightToKg(exerciseUnit)
                contextLinesMap[exerciseNameToSlug(ex.name)] = if (ex.weight > 0.0) {
                    "Plan starting weight: $suggestedPreferred $exerciseUnit"
                } else {
                    "First session suggestion (Beginner Base): $suggestedPreferred $exerciseUnit"
                }
            } else {
                val daysSince = com.apexfit.app.utils.getDaysBetweenClamped(lastSet.date, today)

                val lastWeightLbs = lastSet.weight * com.apexfit.app.utils.AppConstants.KG_TO_LBS

                val muscleReadinessDetail = readinessScore?.muscleDetails?.firstOrNull { it.muscleGroup.equals(ex.muscleGroup, ignoreCase = true) }
                val readinessPercent = muscleReadinessDetail?.readinessPercent

                val _allSets = (allHistorySets[exerciseNameToSlug(ex.name)] ?: emptyList<ExerciseSet>())
                    .filter { !it.isWarmup && it.completed }
                val _latestSessionId = _allSets.firstOrNull()?.sessionId
                val allLastSets = if (_latestSessionId != null) {
                    _allSets.filter { it.sessionId == _latestSessionId }.take(ex.sets)
                } else {
                    emptyList()
                }.map { s ->
                        com.apexfit.app.ui.models.UiExerciseSet(
                            id = s.id,
                            // AUDIT FIX (BUG-V4-011): progression engine contract is lbs for
                            // ALL users. s.weight is canonical kg since schema v21, so convert
                            // unconditionally. The display-unit branch was inverting the contract.
                            weight = s.weight * com.apexfit.app.utils.AppConstants.KG_TO_LBS,
                            reps = s.reps,
                            rpe = s.rpe,
                            isWarmup = s.isWarmup,
                            completed = s.completed,
                            exerciseName = s.exerciseName,
                            sessionId = s.sessionId,
                            muscleGroup = s.muscleGroup
                        )
                    }  
                  
                val recoveryMultiplier = readinessPercent  
                    ?.let { it / 100.0 }  
                    ?.coerceIn(0.7, 1.0)  
                    ?: 1.0  
                  
                val stalledCount = try { dao.getStalledCountForExercise(exerciseNameToSlug(ex.name)) } catch (e: Exception) { 0 }
                val progressionResult = com.apexfit.app.utils.ProgressionEngine.calculateProgressiveWeight(  
                    exerciseId = exerciseNameToSlug(ex.name),  
                    lastSessionSets = allLastSets.ifEmpty {  
                        listOf(com.apexfit.app.ui.models.UiExerciseSet(  
                            weight = lastWeightLbs,  
                            reps = ex.repsMin,  
                            rpe = 7,  
                            isWarmup = false,  
                            completed = true  
                        ))  
                    },  
                    repsMin = ex.repsMin,  
                    repsMax = ex.repsMax,  
                    targetSets = ex.sets,  
                    recoveryMultiplier = recoveryMultiplier,  
                    currentWeight = lastWeightLbs,  
                    exerciseType = exType,
                    consecutiveStalledSessions = stalledCount,
                    incrementOverrideKg = metadataMap[exerciseNameToSlug(ex.name)]?.defaultProgressionIncrementKg
                )
                lastProgressionResults[exerciseNameToSlug(ex.name)] = progressionResult

                val suggestedLbs = progressionResult.newWeight
                
                val suggestedPreferred = if (exerciseUnit.lowercase() in listOf("lb", "lbs")) {
                    val stepLbs = if (exType == "isolation") 1.25 else 2.5
                    (Math.round(suggestedLbs / stepLbs) * stepLbs * 100.0) / 100.0
                } else {
                    val stepKg = if (exType == "isolation") 1.25 else 2.5
                    val converted = suggestedLbs / com.apexfit.app.utils.AppConstants.KG_TO_LBS
                    val rounded = converted.roundToNearestKg(stepKg)
                    if (progressionResult.outcome == OutcomeType.SUCCESS && rounded <= lastSet.weight) {
                        lastSet.weight + stepKg
                    } else {
                        rounded
                    }
                }

                suggestionsMap[exerciseNameToSlug(ex.name)] = suggestedPreferred
                lastWeightMap[exerciseNameToSlug(ex.name)] = lastSet.weight
                val contextSuffix = if (readinessPercent != null) " (Readiness: $readinessPercent%)" else " ($daysSince days ago)"
                val lastWeightDisplay = lastSet.weight.toDisplayWeight(exerciseUnit)
                contextLinesMap[exerciseNameToSlug(ex.name)] = "Last: $lastWeightDisplay $exerciseUnit @ RPE ${lastSet.rpe}$contextSuffix → Suggested: $suggestedPreferred $exerciseUnit. Outcome: ${progressionResult.reason}"
            }
        }

        _lastWeights.value = lastWeightMap
        _weightContextLines.value = contextLinesMap

        // Build the in-memory session with pre-filled sets
        val activeExercises = exercises.map { ex ->
            val exerciseUnit = ex.weightUnit.ifBlank { preferredUnits }
            val suggestedWeight = suggestionsMap[exerciseNameToSlug(ex.name)] ?: ex.weight.toDisplayWeight(exerciseUnit)
            ActiveExercise(
                exerciseId = exerciseNameToSlug(ex.name),
                exerciseName = ex.name,
                muscleGroup = ex.muscleGroup,
                sets = (1..ex.sets).map { setNum ->
                    ActiveSet(
                        setNumber = setNum,
                        weight = suggestedWeight,
                        reps = ex.repsMin,
                        rpe = 7,               // Default — user must set this
                        isWarmup = false,
                        restTakenSeconds = 0,
                        completed = false,
                        weightUnit = exerciseUnit
                    )
                }.toMutableList()
            )
        }.toMutableList()

        _activeSession.value = ActiveSession(
            planSessionId = planSession.id.toString(),
            sessionType = planSession.label,
            startTime = System.currentTimeMillis(),
            exercises = activeExercises
        )
        persistSession()

        val session = _activeSession.value ?: return@withContext
        com.apexfit.app.utils.WorkoutForegroundService.start(appContext, session.sessionName, session.startTime)
        } finally {
            _isStartingSession.value = false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE SET
    // ─────────────────────────────────────────────────────────────

    /**
     * Called whenever user changes weight, reps, RPE, or checks off a set.
     * Mutates in memory — no database write.
     */
    fun updateSet(
        exerciseId: String,
        setIndex: Int,
        weight: Double,
        reps: Int,
        rpe: Int,
        restTakenSeconds: Int,
        completed: Boolean,
        repsInReserve: Int? = null
    ) {
        val session = _activeSession.value ?: return

        // Deep copy of exercises list and inner sets to avoid in-place mutation
        val updatedExercises = session.exercises.map { ex ->
            val updatedSets = ex.sets.map { it.copy() }.toMutableList()
            ex.copy(sets = updatedSets)
        }.toMutableList()

        val exercise = updatedExercises.find { it.exerciseId == exerciseId } ?: return

        if (setIndex < exercise.sets.size) {
            val rpeVal = rpe.coerceIn(1, 10)
            val rirVal = repsInReserve ?: exercise.sets[setIndex].repsInReserve
            exercise.sets[setIndex] = exercise.sets[setIndex].copy(
                weight = weight,
                reps = reps,
                rpe = rpeVal,
                restTakenSeconds = restTakenSeconds,
                completedAt = if (completed) System.currentTimeMillis() else 0L,
                completed = completed,
                repsInReserve = rirVal
            )
            // Trigger StateFlow emission with a completely new reference and nested elements
            _activeSession.value = session.copy(exercises = updatedExercises)
            persistSession()
        }
    }

    /**
     * Adds an extra set to the given exercise in-memory.
     */
    fun addCustomSet(exerciseId: String) {
        val session = _activeSession.value ?: return

        // Deep copy of exercises list and inner sets to avoid in-place mutation
        val updatedExercises = session.exercises.map { ex ->
            val updatedSets = ex.sets.map { it.copy() }.toMutableList()
            ex.copy(sets = updatedSets)
        }.toMutableList()

        val exercise = updatedExercises.find { it.exerciseId == exerciseId } ?: return
        val lastSet = exercise.sets.lastOrNull()
        val newSetNum = exercise.sets.size + 1
        val suggestedWeight = lastSet?.weight ?: 50.0
        val suggestedReps = lastSet?.reps ?: 10
        val suggestedRpe = lastSet?.rpe ?: 7
        val suggestedWeightUnit = lastSet?.weightUnit ?: "kg"
        exercise.sets.add(
            ActiveSet(
                setNumber = newSetNum,
                weight = suggestedWeight,
                reps = suggestedReps,
                rpe = suggestedRpe,
                isWarmup = false,
                restTakenSeconds = 0,
                completed = false,
                weightUnit = suggestedWeightUnit
            )
        )
        // Trigger StateFlow emission with a completely new reference and nested elements
        _activeSession.value = session.copy(exercises = updatedExercises)
        persistSession()
    }


    private fun persistSession() {
        persistJob.value?.cancel()
        persistJob.value = scope.launch {
            delay(500)
            val session = _activeSession.value
            try {
                if (session != null) {
                    dataStore.saveActiveSessionJson(gson.toJson(session))
                } else {
                    dataStore.saveActiveSessionJson(null)
                }
            } catch (e: Exception) {
                android.util.Log.e("WorkoutSessionManager", "Session persist failed", e)
            }
        }
    }

    fun clearPersistedSession() {
        lastProgressionResults.clear()
        persistJob.value?.cancel()
        com.apexfit.app.utils.WorkoutForegroundService.stop(appContext)
        scope.launch {
            dataStore.saveActiveSessionJson(null)
        }
        _activeSession.value = null
        _lastWeights.value = emptyMap()
        _weightContextLines.value = emptyMap()
        _pendingPRWarnings.value = emptyMap()
    }

    /**
     * Checks if the given weight/reps would beat the user's all-time record
     * for this exercise. Call this before the user taps complete on a set
     * to show the PR badge in the UI.
     */
    suspend fun checkPRPreview(exerciseId: String, weight: Double, reps: Int) {
        val prs = try {
            repository.getPRsForExercise(exerciseId)
        } catch (e: Exception) {
            android.util.Log.e("ApexFit", "checkPRPreview DB read failed", e)
            return
        }
        val currentMaxWeight = prs.firstOrNull { it.type == "max_weight" }?.value ?: 0.0
        val currentEstimated1RM = prs.firstOrNull { it.type == "estimated_1rm" }?.value ?: 0.0
        val newEstimated1RM = weight * (1.0 + reps / 30.0)

        val warnings = _pendingPRWarnings.value.toMutableMap()
        if (weight > currentMaxWeight || newEstimated1RM > currentEstimated1RM) {
            warnings[exerciseId] = weight
        } else {
            warnings.remove(exerciseId)
        }
        _pendingPRWarnings.value = warnings
    }

    // ─────────────────────────────────────────────────────────────
    // COMMIT — ATOMIC ROOM WRITE
    // ─────────────────────────────────────────────────────────────

    /**
     * The only path that touches Room. Called when user taps FINISH WORKOUT.
     * Writes TrainingSession + all ExerciseSets in one transaction.
     * Returns the completed session stats for the summary screen.
     */
    suspend fun commitToDatabase(
        sessionFeel: Int,
        completedSetsOnly: Boolean = true,
        isPartial: Boolean = false
    ): SessionCommitResult = withContext(Dispatchers.IO) {
        // AUDIT FIX (BUG-V4-003): cancel any pending persist before we clear
        // state, so a debounced saver cannot write stale data after the commit's
        // null write.
        persistJob.value?.cancel()

        val session = _activeSession.value
            ?: return@withContext SessionCommitResult.empty()

        val sessionId = UUID.randomUUID().toString()
        val durationMinutes = ((System.currentTimeMillis() - session.startTime).toDouble() / 60_000.0)
            .let { it.roundToInt() }
            .coerceAtLeast(1)

        // Flatten all sets, filter to completed if needed
        val allSets = session.exercises.flatMap { exercise ->
            exercise.sets
                .filter { if (completedSetsOnly) it.completed else true }
                .map { activeSet ->
                    ExerciseSet(
                        sessionId = sessionId,
                        exerciseId = exercise.exerciseId,
                        exerciseName = exercise.exerciseName,
                        muscleGroup = exercise.muscleGroup,
                        weight = activeSet.weight.fromDisplayWeightToKg(activeSet.weightUnit),
                        reps = activeSet.reps,
                        rpe = activeSet.rpe,
                        isWarmup = activeSet.isWarmup,
                        restTaken = activeSet.restTakenSeconds,
                        completed = activeSet.completed,
                        repsInReserve = activeSet.repsInReserve,
                        effectiveSetValue = com.apexfit.app.utils.ProgressionEngine.calculateEffectiveSetValue(activeSet.rpe),
                        weightUnit = "kg"
                    )
                }
        }

        val trainingSession = TrainingSession(
            id = sessionId,
            date = com.apexfit.app.utils.DateTimeUtils.todayDateString(),
            sessionType = session.sessionType,
            completed = completedSetsOnly && !isPartial,
            durationMinutes = durationMinutes,
            sessionFeel = sessionFeel.coerceIn(1, 5),
            planSessionId = session.planSessionId.toLongOrNull(),
            status = if (isPartial) "partial" else "completed",
            readinessScoreAtStart = session.readinessScore,
            notes = session.notes,
            startedAt = session.startTime,
            completedAt = System.currentTimeMillis()
        )

        // Single atomic transaction — both rows or neither
        val prs = try {
            evaluatePRs(allSets)
        } catch (e: Exception) {
            android.util.Log.e("ApexFit", "evaluatePRs failed, proceeding with no PRs", e)
            emptyList()
        }
        repository.insertSessionWithPRsAtomic(trainingSession, allSets, prs)

        val db = ServiceLocator.database(appContext)
        db.withTransaction {
            lastProgressionResults.forEach { (exerciseId, result) ->
                try {
                    val r = result as? com.apexfit.app.utils.ProgressionEngine.ProgressionResult ?: return@forEach
                    val current = dao.getStalledCountForExercise(exerciseId)
                    val newCount = when (r.outcome.name) {
                        "SUCCESS", "PROGRESSING", "PLATEAU" -> 0
                        "STALLED" -> current + 1
                        else -> current
                    }
                    dao.updateStalledCount(exerciseId, newCount)
                } catch (e: Exception) { /* ignore */ }
            }
        }
        lastProgressionResults.clear()

        val totalVolume = allSets
            .filter { !it.isWarmup }
            .sumOf { it.weight * it.reps }

        // Use ACTUAL new PRs from evaluatePRs, not the preview warnings
        val confirmedPRs = prs.map { it.exerciseId }.distinct()

        // Clear state — ALWAYS runs after successful insert
        _activeSession.value = null
        _lastWeights.value = emptyMap()
        _weightContextLines.value = emptyMap()
        _pendingPRWarnings.value = emptyMap()
        dataStore.saveActiveSessionJson(null)

        SessionCommitResult(
            sessionId = sessionId,
            durationMinutes = durationMinutes,
            totalVolumeTonnes = totalVolume / 1000.0,
            setsCompleted = allSets.count { it.completed && !it.isWarmup },
            confirmedPRs = confirmedPRs,
            sessionType = session.sessionType
        )
    }

    // ─────────────────────────────────────────────────────────────
    // PARTIAL SAVE / DISCARD
    // ─────────────────────────────────────────────────────────────

    /** Called from "Save partial session?" dialog — yes path */
    suspend fun savePartialAndExit(sessionFeel: Int): SessionCommitResult {
        return commitToDatabase(
            sessionFeel,
            completedSetsOnly = true,
            isPartial = true
        )
    }

    /** Called from "Save partial session?" dialog — no path */
    fun discardAndExit() {
        com.apexfit.app.utils.WorkoutForegroundService.stop(appContext)
        clearPersistedSession()
    }

    val hasCompletedSets: Boolean
        get() = _activeSession.value
            ?.exercises
            ?.flatMap { it.sets }
            ?.any { it.completed }
            ?: false

    // ─────────────────────────────────────────────────────────────
    // PR EVALUATION — runs after commit
    // ─────────────────────────────────────────────────────────────

    private suspend fun evaluatePRs(sets: List<ExerciseSet>): List<PersonalRecord> {
        val newPRs = mutableListOf<PersonalRecord>()
        sets.filter { !it.isWarmup && it.completed }
            .groupBy { it.exerciseId }
            .forEach { (exerciseId, exerciseSets) ->

                val maxWeight = exerciseSets.maxOfOrNull { it.weight } ?: return@forEach
                val totalVolume = exerciseSets.sumOf { it.weight * it.reps }
                val maxEstimated1RM = exerciseSets.maxOfOrNull {
                    it.weight * (1.0 + it.reps / 30.0)
                } ?: return@forEach

                val existing = repository.getPRsForExercise(exerciseId)
                val today = com.apexfit.app.utils.DateTimeUtils.todayDateString()

                val prevWeight = existing.firstOrNull { it.type == "max_weight" }?.value ?: 0.0
                val prevVolume = existing.firstOrNull { it.type == "volume" }?.value ?: 0.0
                val prev1RM = existing.firstOrNull { it.type == "estimated_1rm" }?.value ?: 0.0

                if (maxWeight > prevWeight) {
                    newPRs.add(PersonalRecord("${exerciseId}_max_weight", exerciseId, "max_weight", maxWeight, today))
                }
                if (totalVolume > prevVolume) {
                    newPRs.add(PersonalRecord("${exerciseId}_volume", exerciseId, "volume", totalVolume, today))
                }
                if (maxEstimated1RM > prev1RM) {
                    newPRs.add(PersonalRecord("${exerciseId}_estimated_1rm", exerciseId, "estimated_1rm", maxEstimated1RM, today))
                }
            }
        return newPRs
    }

    private fun exerciseNameToSlug(name: String): String {
        return com.apexfit.app.utils.exerciseNameToSlug(name)
    }

    private fun Double.roundToNearestKg(stepKg: Double): Double =
        (Math.round(this / stepKg) * stepKg * 100.0) / 100.0
}

// ── Result returned to the session complete screen ───────────────

data class SessionCommitResult(
    val sessionId: String,
    val durationMinutes: Int,
    val totalVolumeTonnes: Double,  // in tonnes (kg / 1000)
    val setsCompleted: Int,
    val confirmedPRs: List<String>, // list of exerciseIds with new PRs
    val sessionType: String
) {
    companion object {
        fun empty() = SessionCommitResult("", 0, 0.0, 0, emptyList(), "")
    }
}

private val com.apexfit.app.data.ActiveSession.sessionName: String get() = this.sessionType