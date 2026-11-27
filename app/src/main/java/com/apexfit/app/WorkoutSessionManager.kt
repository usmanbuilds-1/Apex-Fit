package com.apexfit.app
import androidx.room.withTransaction
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.data.*
import com.apexfit.app.utils.ProgressionEngine.OutcomeType
import com.apexfit.app.utils.toDisplayWeight
import com.apexfit.app.utils.fromDisplayWeightToKg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private var persistJob: Job? = null
    private val _restoredFromDisk = MutableStateFlow(false)
    val restoredFromDisk: StateFlow<Boolean> = _restoredFromDisk.asStateFlow()

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

    private val restoreJob: Job = scope.launch {
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
                        _restoredFromDisk.value = true
                        _weightContextLines.value = restored.exercises.mapNotNull { exercise ->
                            exercise.loadContext?.let { exercise.exerciseId to it }
                        }.toMap()
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
        val planned = _activeSession.value?.exercises?.firstOrNull { it.exerciseId == exerciseId }?.restSeconds
        if (planned != null && planned > 0) return planned.coerceIn(15, 900)
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
     * Initializes first-exposure calibration or uses the latest completed working sets.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun startSession(
        planSession: PlanSession,
        exercises: List<PlanExercise>,
        userWeight: Double,
        userHeight: Double,
        preferredUnits: String
    ) = withContext(Dispatchers.IO) {
        // Startup restore and a new workout must never race each other. Await the
        // persisted-session read before deciding whether a session is already active.
        restoreJob.join()
        if (!_isStartingSession.compareAndSet(false, true)) return@withContext
        val slugByExercise = exercises.associate { it.name to it.exerciseId.ifBlank { exerciseNameToSlug(it.name) } }
        val ids = exercises.mapNotNull { slugByExercise[it.name] }
        try {
            if (_activeSession.value != null) return@withContext
            val lastWeightMap = mutableMapOf<String, Double>()
            val suggestionsMap = mutableMapOf<String, Double>()
            val contextLinesMap = mutableMapOf<String, String>()

            val today = com.apexfit.app.utils.DateTimeUtils.todayDateString()

        // Calculate per-muscle readiness
        val readinessScore = try {
            // Read the already-cached rich sessions — no DB round-trip needed
            val completedRichSessions = repository.richSessionsFlow.value
                .map { rs ->
                    com.apexfit.app.utils.TrainingSession(
                        date = rs.date,
                        sessionType = rs.sessionType,
                        completed = rs.completed,
                        sessionFeel = rs.sessionFeel,
                        durationMinutes = rs.durationMinutes,
                        startedAt = rs.startedAt,
                        completedAt = rs.completedAt,
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

            // Parallel reads: launch all independent profile/log reads concurrently and await together
            val nutritionLogDeferred = async { repository.getAllNutritionEntriesFlow().firstOrNull() ?: emptyList() }
            val calorieTargetDeferred = async { repository.getCalorieTargetFlow().firstOrNull() ?: com.apexfit.app.UserDefaults.CALORIES }
            val userGoalDeferred = async { repository.getGoalFlow().firstOrNull() ?: "Maintain Weight" }
            val weeklyWorkoutsDeferred = async { repository.getTrainingFrequencyFlow().first() }

            val nutritionLog = nutritionLogDeferred.await()
            val calorieTarget = calorieTargetDeferred.await()
            val userGoal = userGoalDeferred.await()
            val weeklyWorkouts = weeklyWorkoutsDeferred.await()
            val latestWeight = repository.getCurrentWeightFlow().firstOrNull() ?: com.apexfit.app.UserDefaults.WEIGHT_KG
            val targets = com.apexfit.app.utils.MacroEngine.calcMacroTargets(
                calorieTarget = calorieTarget,
                bodyWeightKg = latestWeight,
                goal = userGoal,
                weeklyTrainingSessions = weeklyWorkouts
            )

            metadataMap = dao.getMetadataForExercises(ids)
                .associateBy { it.exerciseId }

            com.apexfit.app.utils.ReadinessFinal.buildReadinessInputs(
                completedSessions = completedRichSessions,
                todayExercises = exercises,
                nutritionLog = nutritionLog,
                targets = targets,
                bodyWeightKg = latestWeight,
                metadata = metadataMap
            )
        } catch (e: Exception) {
            null
        }

        if (metadataMap.isEmpty()) {
            metadataMap = dao.getMetadataForExercises(ids)
                .associateBy { it.exerciseId }
        }
        val lastSets = dao.getLastSetsWithDateForExercises(ids).groupBy { it.exerciseId }
        val exerciseCategories = dao.getExercisesByIds(ids).associate { it.id to it.category }

        val stalledCounts = try {
            dao.getStalledCountsForExercises(ids).associate { it.exerciseId to it.stalledCount }
        } catch (e: Exception) {
            emptyMap()
        }

        exercises.forEach { ex ->
            val exerciseUnit = preferredUnits.ifBlank { "kg" }
            val exerciseSlug = slugByExercise[ex.name] ?: exerciseNameToSlug(ex.name)
            val exerciseHistory = lastSets[exerciseSlug].orEmpty()
            val lastSet = exerciseHistory.firstOrNull()
            val isBodyweight = exerciseCategories[exerciseSlug].equals("Bodyweight", ignoreCase = true)
            val exType = com.apexfit.app.utils.ProgressionEngine.getExerciseType(ex.name, ex.muscleGroup)

            val suggestedPreferred: Double
            if (lastSet == null) {
                val calibration = com.apexfit.app.utils.StartingLoadCalibrationBuilder
                    .forFirstExposure(ex, exerciseCategories[exerciseSlug].orEmpty())
                suggestionsMap[exerciseSlug] = calibration.initialLoadKg.toDisplayWeight(exerciseUnit)
                lastWeightMap[exerciseSlug] = calibration.initialLoadKg
                contextLinesMap[exerciseSlug] = calibration.guidance
            } else if (isBodyweight && ex.weight <= 0.0 && lastSet.weight <= 0.0) {
                suggestionsMap[exerciseSlug] = 0.0
                lastWeightMap[exerciseSlug] = 0.0
                contextLinesMap[exerciseSlug] = "Bodyweight · record added load only if you use it."
            } else {
                val daysSince = com.apexfit.app.utils.getDaysBetweenClamped(lastSet.date, today)

                val lastWeightLbs = lastSet.weight * com.apexfit.app.utils.AppConstants.KG_TO_LBS

                // The DAO returns the latest set first so lastSet above stays the
                // most recent load for context. Progression, however, must evaluate
                // the prescribed sets in the order they were performed.
                val allLastSets = exerciseHistory.sortedBy { it.id }.map { previous ->
                    com.apexfit.app.ui.models.UiExerciseSet(
                        id = previous.id,
                        weight = previous.weight * com.apexfit.app.utils.AppConstants.KG_TO_LBS,
                        reps = previous.reps,
                        rpe = previous.rpe,
                        isWarmup = previous.isWarmup,
                        completed = previous.completed,
                        exerciseName = previous.exerciseName,
                        sessionId = previous.sessionId,
                        muscleGroup = previous.muscleGroup
                    )
                }

                // Progression is based on actual completed reps and effort;
                // a heuristic load index is not a calibrated weight multiplier.
                val recoveryMultiplier = 1.0
                  
                val stalledCount = stalledCounts[exerciseSlug] ?: 0
                val isImperial = exerciseUnit.lowercase() in listOf("lb", "lbs")
                val metadataIncrementKg = metadataMap[exerciseSlug]
                    ?.defaultProgressionIncrementKg
                    ?.takeIf { it > 0.0 }
                val fallbackStep = if (exType == "isolation") 1.25 else 2.5

                // The engine's canonical input is kg metadata converted internally
                // to pounds. Custom exercises have no seed metadata, so derive a
                // canonical override from the user's actual increment grid instead
                // of mixing a pound fallback with a kilogram boundary.
                val progressionIncrementKg = metadataIncrementKg ?: if (isImperial) {
                    fallbackStep / com.apexfit.app.utils.AppConstants.KG_TO_LBS
                } else {
                    fallbackStep
                }

                val progressionResult = com.apexfit.app.utils.ProgressionEngine.calculateProgressiveWeight(  
                    exerciseId = exerciseSlug,  
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
                    incrementOverrideKg = progressionIncrementKg
                )
                lastProgressionResults[exerciseSlug] = progressionResult

                val suggestedLbs = progressionResult.newWeight

                // Quantise once, at the user-facing unit boundary. Metric seed
                // metadata already describes the intended increment (1 kg, 1.25 kg,
                // 2 kg, 2.5 kg, 5 kg). For imperial display, map that equivalent to
                // a practical 1.25/2.5 lb grid rather than quantising in both units.
                val preferredStep = if (metadataIncrementKg != null) {
                    if (isImperial) {
                        val hardwareGrid = fallbackStep
                        val equivalentLbs = metadataIncrementKg * com.apexfit.app.utils.AppConstants.KG_TO_LBS
                        (Math.round(equivalentLbs / hardwareGrid) * hardwareGrid)
                            .coerceAtLeast(hardwareGrid)
                    } else {
                        metadataIncrementKg
                    }
                } else {
                    fallbackStep
                }

                val lastPreferred = if (isImperial) {
                    lastSet.weight.toDisplayWeight(exerciseUnit)
                } else {
                    lastSet.weight
                }

                val suggestedPreferred = when (progressionResult.outcome) {
                    OutcomeType.STALLED, OutcomeType.PROGRESSING -> lastPreferred
                    OutcomeType.SUCCESS -> {
                        val rawDeltaPreferred = if (isImperial) {
                            suggestedLbs - lastWeightLbs
                        } else {
                            (suggestedLbs - lastWeightLbs) / com.apexfit.app.utils.AppConstants.KG_TO_LBS
                        }
                        val quantizedDelta = if (rawDeltaPreferred >= preferredStep / 2.0) {
                            (Math.round(rawDeltaPreferred / preferredStep) * preferredStep)
                                .coerceAtLeast(preferredStep)
                        } else {
                            0.0
                        }
                        val candidate = lastPreferred + quantizedDelta
                        val finalPreferred = if (isImperial && quantizedDelta > 0.0) {
                            // Stored history is canonical kg, so converting it to lb
                            // can produce values such as 100.09. On a successful
                            // progression, snap the *displayed target* to the imperial
                            // hardware grid without changing hold semantics.
                            val snapped = Math.round(candidate / preferredStep) * preferredStep
                            if (snapped <= lastPreferred) lastPreferred + preferredStep else snapped
                        } else {
                            candidate
                        }
                        Math.round(finalPreferred * 100.0) / 100.0
                    }
                    OutcomeType.PLATEAU -> {
                        val rawTarget = lastPreferred * 0.9
                        val quantizedTarget = Math.floor(rawTarget / preferredStep) * preferredStep
                        Math.round(quantizedTarget.coerceAtLeast(0.0) * 100.0) / 100.0
                    }
                }

                suggestionsMap[exerciseSlug] = suggestedPreferred
                lastWeightMap[exerciseSlug] = lastSet.weight
                val contextSuffix = " ($daysSince days ago)"
                val lastWeightDisplay = lastSet.weight.toDisplayWeight(exerciseUnit)
                contextLinesMap[exerciseSlug] = "Last: $lastWeightDisplay $exerciseUnit @ RPE ${lastSet.rpe}$contextSuffix → Suggested: $suggestedPreferred $exerciseUnit. Outcome: ${progressionResult.reason}"
            }
        }

        _lastWeights.value = lastWeightMap
        _weightContextLines.value = contextLinesMap

        val proposedGoal = dao.getAllPlans().firstOrNull { it.id == planSession.planId }?.goal.orEmpty()
        val recentSessions = repository.richSessionsFlow.value
        val fatigueSnapshot = com.apexfit.app.utils.FatigueEngine.calcFatigueToFitnessRatio(recentSessions)
        val deloadRecommended = com.apexfit.app.utils.PlateauEngine.calcDeloadRecommendation(
            trainingLog = recentSessions,
            complianceScore = 0,
            precomputedFatigue = fatigueSnapshot
        ).recommendation.let { it == "deload_now" || it == "deload_soon" }
        val readinessByMuscle = readinessScore?.takeIf { it.finalScore != null }
            ?.muscleDetails.orEmpty().filter { it.confidence != "none" }
            .associateBy { com.apexfit.app.utils.MuscleAliases.getCanonical(it.muscleGroup) }
        val adjustedMuscles = mutableSetOf<String>()
        // Build the in-memory session with pre-filled sets
        val activeExercises = exercises.map { ex ->
            val exerciseUnit = preferredUnits.ifBlank { "kg" }
            val exerciseSlug = slugByExercise[ex.name] ?: exerciseNameToSlug(ex.name)
            val suggestedWeight = suggestionsMap[exerciseSlug] ?: ex.weight.toDisplayWeight(exerciseUnit)
            val muscle = com.apexfit.app.utils.MuscleAliases.getCanonical(ex.muscleGroup)
            val lastLoggedWeight = lastSets[exerciseSlug]?.firstOrNull()?.weight
            val readinessForVolume = readinessByMuscle[muscle]?.readinessPercent?.let { local ->
                minOf(local, readinessScore?.systemicReadiness ?: local)
            }
            val adjustment = if (muscle !in adjustedMuscles && lastLoggedWeight != null) {
                com.apexfit.app.utils.AdaptiveSetVolume.adjustment(
                    goal = proposedGoal,
                    exerciseId = exerciseSlug,
                    baseSets = ex.sets,
                    repsMin = ex.repsMin,
                    sessions = recentSessions,
                    today = java.time.LocalDate.parse(today),
                    readiness = readinessForVolume,
                    loadChanged = kotlin.math.abs(suggestedWeight.fromDisplayWeightToKg(exerciseUnit) - lastLoggedWeight) > 0.05,
                    fatigueRatio = fatigueSnapshot.ratio,
                    deloadRecommended = deloadRecommended
                )
            } else null
            if (adjustment != null) adjustedMuscles.add(muscle)
            ActiveExercise(
                exerciseId = exerciseSlug,
                exerciseName = ex.name,
                muscleGroup = ex.muscleGroup,
                restSeconds = ex.restSeconds.takeIf { it > 0 },
                targetRepsMin = ex.repsMin,
                targetRepsMax = ex.repsMax,
                loadContext = contextLinesMap[exerciseSlug],
                volumeAdjustment = adjustment,
                sets = (1..(adjustment?.adjustedSets ?: ex.sets)).map { setNum ->
                    ActiveSet(
                        setNumber = setNum,
                        weight = suggestedWeight,
                        reps = ex.repsMin,
                        rpe = 0,               // Unrated until the user explicitly selects effort
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
            readinessScore = readinessScore?.finalScore,
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

        // Structural sharing: only copy the affected exercise
        val exercise = session.exercises.find { it.exerciseId == exerciseId } ?: return
        if (setIndex !in exercise.sets.indices) return

        val rpeVal = rpe.coerceIn(0, 10)
        val updatedExercises = session.exercises.map { ex ->
            if (ex.exerciseId == exerciseId) {
                val updatedSets = ex.sets.toMutableList()
                val rirVal = repsInReserve ?: updatedSets[setIndex].repsInReserve
                updatedSets[setIndex] = updatedSets[setIndex].copy(
                    weight = weight,
                    reps = reps,
                    rpe = rpeVal,
                    restTakenSeconds = restTakenSeconds,
                    completedAt = if (completed) System.currentTimeMillis() else 0L,
                    completed = completed,
                    repsInReserve = rirVal
                )
                ex.copy(sets = updatedSets)
            } else {
                ex
            }
        }.toMutableList()

        _activeSession.value = session.copy(exercises = updatedExercises)
        persistSession()
    }

    /**
     * Adds an extra set to the given exercise in-memory.
     */
    fun addCustomSet(exerciseId: String) {
        val session = _activeSession.value ?: return

        val exercise = session.exercises.find { it.exerciseId == exerciseId } ?: return
        val lastSet = exercise.sets.lastOrNull()
        val newSetNum = exercise.sets.size + 1
        val suggestedWeight = lastSet?.weight ?: 50.0
        val suggestedReps = lastSet?.reps ?: 10
        val suggestedRpe = lastSet?.rpe?.takeIf { it in 1..10 } ?: 0
        val suggestedWeightUnit = lastSet?.weightUnit ?: "kg"

        // Structural sharing: only copy the affected exercise
        val updatedExercises = session.exercises.map { ex ->
            if (ex.exerciseId == exerciseId) {
                val updatedSets = ex.sets.toMutableList()
                updatedSets.add(
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
                ex.copy(sets = updatedSets)
            } else {
                ex
            }
        }.toMutableList()

        _activeSession.value = session.copy(exercises = updatedExercises)
        persistSession()
    }


    fun undoSetVolumeAdjustment(exerciseId: String): Boolean {
        val session = _activeSession.value ?: return false
        val exercise = session.exercises.firstOrNull { it.exerciseId == exerciseId } ?: return false
        val sets = exercise.volumeAdjustment?.revert(exercise.sets) ?: return false
        val restored = exercise.copy(sets = sets.toMutableList(), volumeAdjustment = null)
        val updated = session.copy(exercises = session.exercises.map {
            if (it === exercise) restored else it
        }.toMutableList())
        if (!_activeSession.compareAndSet(session, updated)) return false
        persistSession()
        return true
    }

    private fun persistSession() {
        persistJob?.cancel()
        persistJob = scope.launch {
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
        persistJob?.cancel()
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
        persistJob?.cancel()

        val session = _activeSession.value
            ?: return@withContext SessionCommitResult.empty()

        val sessionId = UUID.randomUUID().toString()
        val completedAt = System.currentTimeMillis()
        val durationMinutes = ((completedAt - session.startTime).coerceAtLeast(0L).toDouble() / 60_000.0)
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

        val volumeNotes = session.exercises.mapNotNull { exercise ->
            exercise.volumeAdjustment?.takeIf { exercise.sets.any { it.completed && !it.isWarmup } }?.let {
                "${exercise.exerciseName}: ${it.baseSets} → ${it.adjustedSets} planned sets. ${it.reason}"
            }
        }
        val sessionNotes = (listOfNotNull(session.notes?.takeIf(String::isNotBlank)) + volumeNotes)
            .joinToString("\n").takeIf(String::isNotBlank)

        val trainingSession = TrainingSession(
            id = sessionId,
            // Attribute a cross-midnight workout to the local calendar day it began.
            date = com.apexfit.app.utils.DateTimeUtils.dateStringForEpochMillis(session.startTime),
            sessionType = session.sessionType,
            completed = completedSetsOnly && !isPartial,
            durationMinutes = durationMinutes,
            sessionFeel = sessionFeel.coerceIn(1, 5),
            planSessionId = session.planSessionId.toLongOrNull(),
            status = if (isPartial) "partial" else "completed",
            readinessScoreAtStart = session.readinessScore,
            notes = sessionNotes,
            startedAt = session.startTime,
            completedAt = completedAt
        )

        // Single atomic transaction — both rows or neither
        val prs = try {
            evaluatePRs(allSets)
        } catch (e: Exception) {
            android.util.Log.e("ApexFit", "evaluatePRs failed, proceeding with no PRs", e)
            emptyList()
        }
        val db = AppDatabase.getDatabase(appContext)
        db.withTransaction {
            repository.insertSessionWithPRsAtomic(trainingSession, allSets, prs)
            val exerciseIds = lastProgressionResults.keys.toList()
            val currentCounts = dao.getStalledCountsForExercises(exerciseIds)
                .associate { it.exerciseId to it.stalledCount }
            lastProgressionResults.forEach { (exerciseId, result) ->
                if (!trainingSession.completed || allSets.none { it.exerciseId == exerciseId && it.completed && !it.isWarmup }) return@forEach
                val r = result as? com.apexfit.app.utils.ProgressionEngine.ProgressionResult ?: return@forEach
                val current = currentCounts[exerciseId] ?: 0
                val newCount = when (r.outcome.name) {
                    "SUCCESS", "PROGRESSING", "PLATEAU" -> 0
                    "STALLED" -> current + 1
                    else -> current
                }
                dao.updateStalledCount(exerciseId, newCount)
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
            sessionType = session.sessionType,
            records = prs,
            isPartial = isPartial
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
    val sessionType: String,
    val records: List<com.apexfit.app.data.PersonalRecord> = emptyList(),
    val isPartial: Boolean = false
) {
    companion object {
        fun empty() = SessionCommitResult("", 0, 0.0, 0, emptyList(), "")
    }
}

private val com.apexfit.app.data.ActiveSession.sessionName: String get() = this.sessionType
