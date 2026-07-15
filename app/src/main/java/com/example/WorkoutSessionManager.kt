package com.example
import com.example.domain.repository.FitnessRepository
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val scope: CoroutineScope
) {

    private val gson = Gson()
    private val persistJob = MutableStateFlow<kotlinx.coroutines.Job?>(null)

    init {
        scope.launch {
            val json = dataStore.activeSessionJsonFlow.firstOrNull()
            if (!json.isNullOrEmpty()) {
                try {
                    val restored = gson.fromJson(json, ActiveSession::class.java)
                    if (restored != null) {
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

        val lastWeightMap = mutableMapOf<String, Double>()
        val suggestionsMap = mutableMapOf<String, Double>()
        val contextLinesMap = mutableMapOf<String, String>()

        val today = com.example.utils.DateTimeUtils.todayDateString()

        // Calculate per-muscle readiness
        val readinessScore = try {
            val sessionsRaw = repository.getAllCompletedSessions()
            val setsRaw = repository.getAllExerciseSets()
            
            // Build rich sessions
            val setsBySession = setsRaw.groupBy { it.sessionId }
            val completedRichSessions = sessionsRaw.map { sessionObj ->
                val sessionSets = setsBySession[sessionObj.id.toString()] ?: emptyList()
                val sessionExercises = sessionSets
                    .groupBy { it.exerciseId }
                    .map { (exerciseId, exSets) ->
                        com.example.utils.ExerciseLog(
                            id = exerciseId,
                            name = exSets.first().exerciseName,
                            muscleGroup = exSets.first().muscleGroup,
                            sets = exSets.map { s ->
                                com.example.utils.ExerciseSet(
                                    weight = s.weight,
                                    reps = s.reps,
                                    rpe = s.rpe,
                                    isWarmup = s.isWarmup,
                                    completed = s.completed
                                )
                            }
                        )
                    }
                com.example.utils.TrainingSession(
                    date = sessionObj.date,
                    sessionType = sessionObj.sessionType,
                    completed = sessionObj.completed,
                    sessionFeel = sessionObj.sessionFeel,
                    durationMinutes = sessionObj.durationMinutes,
                    exercises = sessionExercises
                )
            }

            val nutritionLog = repository.getAllNutritionEntriesFlow().firstOrNull() ?: emptyList()
            val calorieTarget = repository.getCalorieTargetFlow().firstOrNull() ?: com.example.UserDefaults.CALORIES
            val latestWeight = repository.getCurrentWeightFlow().firstOrNull() ?: com.example.UserDefaults.WEIGHT_KG
            
            val proteinTarget = (latestWeight * com.example.UserDefaults.PROTEIN_PER_KG).roundToInt().coerceIn(100, 250)
            val fatTarget = (calorieTarget * 0.25 / 9.0).roundToInt().coerceIn(45, 120)
            val carbsTarget = ((calorieTarget - (proteinTarget * 4) - (fatTarget * 9)) / 4.0).roundToInt().coerceIn(100, 500)
            val targets = com.example.utils.NutritionTargets(
                calories = calorieTarget,
                protein = proteinTarget,
                carbs = carbsTarget,
                fat = fatTarget,
                weeklyTrainingSessions = 4
            )

            com.example.utils.ReadinessFinal.buildReadinessInputs(
                completedSessions = completedRichSessions,
                todayExercises = exercises,
                nutritionLog = nutritionLog,
                targets = targets
            )
        } catch (e: Exception) {
            null
        }

        exercises.forEach { ex ->
            val lastSet = repository.getLastSetForExercise(ex.id.toString())
            val exType = com.example.utils.ProgressionEngine.getExerciseType(ex.name, ex.muscleGroup)

            val suggestedLbs: Double
            if (lastSet == null) {
                // First-time or beginner starting weight
                suggestedLbs = com.example.utils.ProgressionEngine.calculateBeginnerStartingWeight(
                    exerciseType = exType,
                    userBodyWeightKg = userWeight,
                    userHeightCm = userHeight
                )

                val suggestedPreferred = if (preferredUnits.lowercase() == "lbs") {
                    suggestedLbs
                } else {
                    val converted = suggestedLbs / 2.205
                    com.example.utils.ProgressionEngine.run { converted.roundToNearest2_5() }
                }

                suggestionsMap[exerciseNameToSlug(ex.name)] = suggestedPreferred
                lastWeightMap[exerciseNameToSlug(ex.name)] = suggestedPreferred
                contextLinesMap[exerciseNameToSlug(ex.name)] = "First session suggestion (Beginner Base): $suggestedPreferred $preferredUnits"
            } else {
                val daysSince = com.example.utils.getDaysBetweenClamped(lastSet.date, today)

                val lastWeightLbs = if (preferredUnits.lowercase() == "lbs") {
                    lastSet.weight
                } else {
                    lastSet.weight * 2.205
                }

                val muscleReadinessDetail = readinessScore?.muscleDetails?.firstOrNull { it.muscleGroup.equals(ex.muscleGroup, ignoreCase = true) }
                val readinessPercent = muscleReadinessDetail?.readinessPercent

                val allLastSets = repository.getLastSetsForExercise(exerciseNameToSlug(ex.name))
                    .filter { !it.isWarmup && it.completed }
                    .take(ex.sets)
                    .map { s ->  
                        com.example.ui.models.UiExerciseSet(  
                            id = s.id,  
                            weight = s.weight,  
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
                  
                val progressionResult = com.example.utils.ProgressionEngine.calculateProgressiveWeight(  
                    exerciseId = exerciseNameToSlug(ex.name),  
                    lastSessionSets = allLastSets.ifEmpty {  
                        listOf(com.example.ui.models.UiExerciseSet(  
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
                    exerciseType = exType  
                )

                val suggestedLbs = progressionResult.newWeight
                
                val suggestedPreferred = if (preferredUnits.lowercase() == "lbs") {
                    suggestedLbs
                } else {
                    val converted = suggestedLbs / 2.205
                    com.example.utils.ProgressionEngine.run { converted.roundToNearest2_5() }
                }

                suggestionsMap[exerciseNameToSlug(ex.name)] = suggestedPreferred
                lastWeightMap[exerciseNameToSlug(ex.name)] = lastSet.weight
                val contextSuffix = if (readinessPercent != null) " (Readiness: $readinessPercent%)" else " ($daysSince days ago)"
                contextLinesMap[exerciseNameToSlug(ex.name)] = "Last: ${lastSet.weight} $preferredUnits @ RPE ${lastSet.rpe}$contextSuffix → Suggested: $suggestedPreferred $preferredUnits. Outcome: ${progressionResult.reason}"
            }
        }

        _lastWeights.value = lastWeightMap
        _weightContextLines.value = contextLinesMap

        // Build the in-memory session with pre-filled sets
        val activeExercises = exercises.map { ex ->
            val suggestedWeight = suggestionsMap[exerciseNameToSlug(ex.name)] ?: ex.weight
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
                        completed = false
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
        exercise.sets.add(
            ActiveSet(
                setNumber = newSetNum,
                weight = suggestedWeight,
                reps = suggestedReps,
                rpe = suggestedRpe,
                isWarmup = false,
                restTakenSeconds = 0,
                completed = false
            )
        )
        // Trigger StateFlow emission with a completely new reference and nested elements
        _activeSession.value = session.copy(exercises = updatedExercises)
        persistSession()
    }


    private fun persistSession() {
        val session = _activeSession.value
        persistJob.value?.cancel()
        persistJob.value = scope.launch {
            delay(500)
            try {
                if (session != null) {
                    dataStore.saveActiveSessionJson(gson.toJson(session))
                } else {
                    dataStore.saveActiveSessionJson(null)
                }
            } catch (e: Exception) {
                android.util.Log.e("WorkoutSessionManager", "persistSession failed", e)
            }
        }
    }

    fun clearPersistedSession() {
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
        val prs = repository.getPRsForExercise(exerciseId)
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
        completedSetsOnly: Boolean = true
    ): SessionCommitResult = withContext(Dispatchers.IO) {

        val session = _activeSession.value
            ?: return@withContext SessionCommitResult.empty()

        val sessionId = UUID.randomUUID().toString()
        val durationMinutes = ((System.currentTimeMillis() - session.startTime) / 60_000L)
            .toInt()
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
                        weight = activeSet.weight,
                        reps = activeSet.reps,
                        rpe = activeSet.rpe,
                        isWarmup = activeSet.isWarmup,
                        restTaken = activeSet.restTakenSeconds,
                        completed = activeSet.completed,
                        repsInReserve = activeSet.repsInReserve,
                        effectiveSetValue = com.example.utils.ProgressionEngine.calculateEffectiveSetValue(activeSet.rpe)
                    )
                }
        }

        val trainingSession = TrainingSession(
            id = sessionId,
            date = com.example.utils.DateTimeUtils.todayDateString(),
            sessionType = session.sessionType,
            completed = completedSetsOnly,
            durationMinutes = durationMinutes,
            sessionFeel = sessionFeel.coerceIn(1, 5),
            planSessionId = session.planSessionId.toLongOrNull(),
            status = if (completedSetsOnly) "completed" else "partial",
            readinessScoreAtStart = session.readinessScore,
            notes = session.notes,
            startedAt = session.startTime,
            completedAt = System.currentTimeMillis()
        )

        // Single atomic transaction — both rows or neither
        val prs = evaluatePRs(allSets)
        repository.insertSessionWithPRsAtomic(trainingSession, allSets, prs)

        // Pattern scan is best-effort — never let it fail the commit
        try {
            repository.scanAndSaveWeeklyPatterns()
        } catch (e: Exception) {
            android.util.Log.e("WorkoutSessionManager", "Pattern scan failed (non-fatal)", e)
        }

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
        return commitToDatabase(sessionFeel, completedSetsOnly = true)
    }

    /** Called from "Save partial session?" dialog — no path */
    fun discardAndExit() {
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
                val today = com.example.utils.DateTimeUtils.todayDateString()

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
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .trim()
    }
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
