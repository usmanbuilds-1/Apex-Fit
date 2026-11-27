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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.apexfit.app.utils.ActiveWorkoutCodec
import com.apexfit.app.utils.WorkoutOccurrenceKeys
import com.apexfit.app.utils.WorkoutResumeState
import com.apexfit.app.data.ActiveRestCheckpoint
import android.os.SystemClock
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
    private val stateLock = Any()
    private val commitMutex = Mutex()
    private val persistenceMutex = Mutex()
    private var committing = false // guarded by stateLock
    private var savingCriticalSet = false // guarded by stateLock
    private var liveRestIdentity: Pair<String, Long>? = null // guarded by stateLock
    private data class PendingRest(val sessionId: String, val key: String, val index: Int, val seconds: Int)
    private val pendingRests = mutableListOf<PendingRest>() // guarded by stateLock
    private var lastReceipt: SessionCommitResult? = null // guarded by commitMutex
    private val _recoveredReceipt = MutableStateFlow<SessionCommitResult?>(null)
    val recoveredReceipt: StateFlow<SessionCommitResult?> = _recoveredReceipt.asStateFlow()
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
        try {
            val raw = dataStore.activeSessionJsonFlow.firstOrNull()
            if (raw == null) {
                recoverCommittedReceipt()
                return@launch
            }
            val decoded = ActiveWorkoutCodec.decode(raw, gson)
            val restored = decoded.session
            if (decoded.migrated) {
                // A v1 payload had no durable ID. Do not guess whether it is an
                // already-committed workout left behind by the old save path.
                check(dao.getTrainingSessionsStartedAt(restored.startTime).isEmpty()) {
                    "Legacy workout may already exist in history"
                }
            } else if (dao.getTrainingSessionById(restored.sessionId) != null) {
                // Exact stable ID is proof of a previous committed transaction.
                // Its history is already saved; do not resume or insert it again.
                recoverCommittedReceipt(restored.sessionId)
                dataStore.saveActiveSessionJson(null)
                return@launch
            }
            if (decoded.migrated || decoded.normalized) {
                dataStore.saveActiveSessionJson(gson.toJson(restored))
            }
            _restoredFromDisk.value = true
            _weightContextLines.value = restored.exercises.mapNotNull { exercise ->
                exercise.loadContext?.let { exercise.executionKey to it }
            }.toMap()
            _activeSession.value = restored
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("WorkoutSessionManager", "Saved workout retained for recovery", e)
            _sessionRestoreFailed.value = true
        }
    }

    // A journal written before the transaction is not proof of a workout.
    // Only a matching committed Room row permits recovery. Always take the
    // set details and counts from Room, not from UI state or the draft.
    private suspend fun recoverCommittedReceipt(expectedId: String? = null) {
        try {
            val (id, json) = dataStore.workoutReceiptFlow.firstOrNull() ?: return
            if (expectedId != null && id != expectedId) return
            val session = dao.getTrainingSessionById(id) ?: return
            val journal = gson.fromJson(json, SessionCommitResult::class.java)
            check(journal.sessionId == id)
            val sets = dao.getSetsForSession(id)
            val catalogIds = sets.filter { it.completed && !it.isWarmup }.map { it.exerciseId }.toSet()
            val earned = journal.records.filter { it.exerciseId in catalogIds && it.date == session.date }
            _recoveredReceipt.value = buildReceipt(session, sets, earned)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // History is still saved; a malformed receipt must never block
            // restoration of the actual workout or invent earned records.
            android.util.Log.e("WorkoutSessionManager", "Receipt recovery unavailable", e)
        }
    }

    fun dismissReceipt(sessionId: String) {
        if (_recoveredReceipt.value?.sessionId == sessionId) _recoveredReceipt.value = null
        scope.launch {
            try {
                dataStore.dismissWorkoutReceipt(sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("WorkoutSessionManager", "Could not dismiss saved receipt", e)
            }
        }
    }

    private fun buildReceipt(session: TrainingSession, sets: List<ExerciseSet>, earned: List<PersonalRecord>): SessionCommitResult {
        val working = sets.filter { it.completed && !it.isWarmup }
        return SessionCommitResult(
            sessionId = session.id,
            durationMinutes = session.durationMinutes,
            totalVolumeTonnes = working.sumOf { it.weight * it.reps } / 1000.0,
            setsCompleted = working.size,
            confirmedPRs = earned.map { it.exerciseId }.distinct(),
            sessionType = session.sessionType,
            records = earned,
            isPartial = session.status == "partial",
            savedSets = sets
        )
    }

    /** Only an explicit user choice may discard an unreadable/ambiguous payload. */
    suspend fun discardUnrestorableSession(): Boolean {
        restoreJob.join()
        if (!_sessionRestoreFailed.value || _activeSession.value != null) return false
        return try {
            persistenceMutex.withLock { dataStore.saveActiveSessionJson(null) }
            _sessionRestoreFailed.value = false
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("WorkoutSessionManager", "Unable to discard saved workout", e)
            false
        }
    }

    fun incrementRestSeconds() {
        _currentRestSeconds.value++
    }

    fun resetRestSeconds() {
        _currentRestSeconds.value = 0
    }

    fun commitRestTaken(exerciseId: String, setIndex: Int, seconds: Int, expectedSessionId: String? = null) {
        synchronized(stateLock) {
        if (committing) return
        val session = _activeSession.value ?: return
        if (expectedSessionId != null && session.sessionId != expectedSessionId) return
        if (savingCriticalSet) {
            pendingRests.add(PendingRest(session.sessionId, exerciseId, setIndex, seconds))
            return
        }
        val exercise = session.exercises.find { it.executionKey == exerciseId } ?: return
        if (setIndex in exercise.sets.indices) {
            val set = exercise.sets[setIndex]
            if (!set.completed) return
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
    }

    fun calculateRestTimeSeconds(exerciseId: String, exerciseType: String, rpe: Int): Int {
        val exercise = _activeSession.value?.exercises?.firstOrNull { it.executionKey == exerciseId }
        val planned = exercise?.restSeconds
        if (planned != null && planned > 0) return planned.coerceIn(15, 900)
        return com.apexfit.app.utils.ProgressionEngine.calculateRestTimeSeconds(
            exerciseType = exerciseType,
            rpe = rpe,
            restSecondsOverride = metadataMap[exercise?.exerciseId ?: exerciseId]?.defaultRestSeconds
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
        if (_sessionRestoreFailed.value) return@withContext
        if (!_isStartingSession.compareAndSet(false, true)) return@withContext
        val catalogIds = exercises.map { it.exerciseId.ifBlank { exerciseNameToSlug(it.name) } }
        val occurrenceKeys = WorkoutOccurrenceKeys.allocate(catalogIds)
        val repeatedCatalogIds = catalogIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val ids = catalogIds.distinct()
        try {
            persistJob?.join()
            if (synchronized(stateLock) { committing || savingCriticalSet || _activeSession.value != null }) return@withContext
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
        // Do not interpret a previous multi-occurrence workout as one prescription.
        val ambiguousHistoryIds = lastSets.filterValues { history ->
            history.map { it.occurrenceIndex }.distinct().size > 1
        }.keys
        val progressionPausedIds = repeatedCatalogIds + ambiguousHistoryIds
        val exerciseCategories = dao.getExercisesByIds(ids).associate { it.id to it.category }

        val stalledCounts = try {
            dao.getStalledCountsForExercises(ids).associate { it.exerciseId to it.stalledCount }
        } catch (e: Exception) {
            emptyMap()
        }

        exercises.forEachIndexed { index, ex ->
            val exerciseUnit = preferredUnits.ifBlank { "kg" }
            val exerciseSlug = catalogIds[index]
            val occurrenceKey = occurrenceKeys[index]
            val exerciseHistory = lastSets[exerciseSlug].orEmpty()
            val lastSet = exerciseHistory.firstOrNull()
            val isBodyweight = exerciseCategories[exerciseSlug].equals("Bodyweight", ignoreCase = true)
            val exType = com.apexfit.app.utils.ProgressionEngine.getExerciseType(ex.name, ex.muscleGroup)

            val suggestedPreferred: Double
            if (exerciseSlug in progressionPausedIds) {
                // Legacy historical sets cannot distinguish separate prescriptions for
                // the same exercise. Honor this occurrence's explicit load, or use
                // first-exposure calibration; don't apply ambiguous automatic progression.
                val calibration = com.apexfit.app.utils.StartingLoadCalibrationBuilder.forFirstExposure(ex, exerciseCategories[exerciseSlug].orEmpty())
                val target = if (ex.weight > 0.0) ex.weight.fromDisplayWeightToKg(ex.weightUnit) else calibration.initialLoadKg
                suggestionsMap[occurrenceKey] = target.toDisplayWeight(exerciseUnit)
                lastWeightMap[occurrenceKey] = target
                contextLinesMap[occurrenceKey] = "Separate exercise prescription · automatic progression paused for repeated or mixed historical entries."
            } else if (lastSet == null) {
                val calibration = com.apexfit.app.utils.StartingLoadCalibrationBuilder
                    .forFirstExposure(ex, exerciseCategories[exerciseSlug].orEmpty())
                suggestionsMap[occurrenceKey] = calibration.initialLoadKg.toDisplayWeight(exerciseUnit)
                lastWeightMap[occurrenceKey] = calibration.initialLoadKg
                contextLinesMap[occurrenceKey] = calibration.guidance
            } else if (isBodyweight && ex.weight <= 0.0 && lastSet.weight <= 0.0) {
                suggestionsMap[occurrenceKey] = 0.0
                lastWeightMap[occurrenceKey] = 0.0
                contextLinesMap[occurrenceKey] = "Bodyweight · record added load only if you use it."
            } else {
                val daysSince = com.apexfit.app.utils.getDaysBetweenClamped(lastSet.date, today)

                val lastWeightLbs = lastSet.weight * com.apexfit.app.utils.AppConstants.KG_TO_LBS

                val muscleReadinessDetail = readinessScore?.muscleDetails?.firstOrNull { it.muscleGroup.equals(ex.muscleGroup, ignoreCase = true) }
                val readinessPercent = muscleReadinessDetail?.readinessPercent.takeIf { readinessScore?.finalScore != null }

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

                val recoveryMultiplier = readinessPercent  
                    ?.let { it / 100.0 }  
                    ?.coerceIn(0.7, 1.0)  
                    ?: 1.0  
                  
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

                suggestionsMap[occurrenceKey] = suggestedPreferred
                lastWeightMap[occurrenceKey] = lastSet.weight
                val contextSuffix = if (readinessPercent != null) " (Readiness: $readinessPercent%)" else " ($daysSince days ago)"
                val lastWeightDisplay = lastSet.weight.toDisplayWeight(exerciseUnit)
                contextLinesMap[occurrenceKey] = "Last: $lastWeightDisplay $exerciseUnit @ RPE ${lastSet.rpe}$contextSuffix → Suggested: $suggestedPreferred $exerciseUnit. Outcome: ${progressionResult.reason}"
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
        ).urgency.let { it == "deload_now" || it == "deload_soon" }
        val readinessByMuscle = readinessScore?.takeIf { it.finalScore != null }
            ?.muscleDetails.orEmpty().filter { it.confidence != "none" }
            .associateBy { com.apexfit.app.utils.MuscleAliases.getCanonical(it.muscleGroup) }
        val adjustedMuscles = mutableSetOf<String>()
        // Build the in-memory session with pre-filled sets
        val activeExercises = exercises.mapIndexed { index, ex ->
            val exerciseUnit = preferredUnits.ifBlank { "kg" }
            val exerciseSlug = catalogIds[index]
            val occurrenceKey = occurrenceKeys[index]
            val suggestedWeight = suggestionsMap[occurrenceKey] ?: ex.weight.toDisplayWeight(exerciseUnit)
            val muscle = com.apexfit.app.utils.MuscleAliases.getCanonical(ex.muscleGroup)
            val lastLoggedWeight = lastSets[exerciseSlug]?.firstOrNull()?.weight
            val readinessForVolume = readinessByMuscle[muscle]?.readinessPercent?.let { local ->
                minOf(local, readinessScore?.systemicReadiness ?: local)
            }
            val adjustment = if (exerciseSlug !in progressionPausedIds && muscle !in adjustedMuscles && lastLoggedWeight != null) {
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
                loadContext = contextLinesMap[occurrenceKey],
                volumeAdjustment = adjustment,
                occurrenceKey = occurrenceKey.takeIf { it != exerciseSlug },
                sourcePlanExerciseId = ex.id.takeIf { it > 0L },
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

        val newSession = ActiveSession(
            cursorIndex = 0,
            skippedOccurrenceIndices = emptyList(),
            planSessionId = planSession.id.toString(),
            sessionType = planSession.label,
            startTime = System.currentTimeMillis(),
            readinessScore = readinessScore?.finalScore,
            exercises = activeExercises
        )
        // Do not expose a workout that has not received its durable identity.
        // A cancelled caller must not leave a saved draft hidden from the
        // current process, allowing a subsequent Start to overwrite it.
        withContext(NonCancellable) {
            persistenceMutex.withLock { dataStore.beginWorkoutDraft(gson.toJson(newSession)) }
            _recoveredReceipt.value = null
            _activeSession.value = newSession
        }
        com.apexfit.app.utils.WorkoutForegroundService.start(appContext, newSession.sessionName, newSession.startTime)
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
    private fun editedSet(
        session: ActiveSession,
        exerciseId: String,
        setIndex: Int,
        weight: Double,
        reps: Int,
        rpe: Int,
        restTakenSeconds: Int,
        completed: Boolean,
        repsInReserve: Int?
    ): ActiveSession? {
        val exercise = session.exercises.find { it.executionKey == exerciseId } ?: return null
        val old = exercise.sets.getOrNull(setIndex) ?: return null
        if (!weight.isFinite() || weight < 0.0 || reps < 0 || (completed && reps == 0)) return null
        val newSets = exercise.sets.toMutableList()
        newSets[setIndex] = old.copy(
            weight = weight, reps = reps, rpe = rpe.coerceIn(0, 10),
            restTakenSeconds = restTakenSeconds.coerceAtLeast(0),
            completedAt = if (completed) old.completedAt.takeIf { it > 0L } ?: System.currentTimeMillis() else 0L,
            completed = completed, repsInReserve = repsInReserve ?: old.repsInReserve
        )
        return session.copy(exercises = session.exercises.map {
            if (it.executionKey == exerciseId) it.copy(sets = newSets) else it
        }.toMutableList())
    }

    fun updateSet(
        exerciseId: String, setIndex: Int, weight: Double, reps: Int, rpe: Int,
        restTakenSeconds: Int, completed: Boolean, repsInReserve: Int? = null
    ) {
        synchronized(stateLock) {
            if (committing || savingCriticalSet) return
            val session = _activeSession.value ?: return
            val old = session.exercises.find { it.executionKey == exerciseId }?.sets?.getOrNull(setIndex) ?: return
            val updated = editedSet(session, exerciseId, setIndex, weight, reps, rpe, restTakenSeconds, completed, repsInReserve) ?: return
            _activeSession.value = updated
            persistSession(immediate = completed || old.completed)
        }
    }

    /**
     * Acknowledge user-confirmed completion/correction only after durable DataStore
     * write. Failed writes leave the previously published set unchanged.
     * Serialize against final commit and reject stale-session callbacks.
     */
    suspend fun updateSetDurably(
        expectedSessionId: String, exerciseId: String, setIndex: Int,
        weight: Double, reps: Int, rpe: Int, restTakenSeconds: Int,
        completed: Boolean, repsInReserve: Int? = null,
        advanceAfterCompletion: Boolean = false
    ): Boolean = mutateDraftDurably(expectedSessionId) { session ->
        if (!completed && session.restCheckpoint?.let { it.exerciseKey == exerciseId && it.setIndex == setIndex } == true)
            return@mutateDraftDurably null
        val edited = editedSet(session, exerciseId, setIndex, weight, reps, rpe,
            restTakenSeconds, completed, repsInReserve) ?: return@mutateDraftDurably null
        if (!advanceAfterCompletion || !completed) return@mutateDraftDurably edited
        val index = edited.exercises.indexOfFirst { it.executionKey == exerciseId }
        if (index < 0 || WorkoutResumeState.cursor(session) != index || session.restCheckpoint != null)
            return@mutateDraftDurably null
        val exercise = edited.exercises[index]
        val done = exercise.sets.all { it.completed || it.isWarmup }
        val positioned = edited.copy(cursorIndex = index)
        val next = if (done) WorkoutResumeState.advance(positioned, index, false) else positioned
        if (done && index == edited.exercises.lastIndex) return@mutateDraftDurably next
        val type = com.apexfit.app.utils.ProgressionEngine.getExerciseType(exercise.exerciseName, exercise.muscleGroup)
        val seconds = calculateRestTimeSeconds(exerciseId, type, rpe).coerceIn(1, 3600)
        val now = SystemClock.elapsedRealtime()
        liveRestIdentity = session.sessionId to now
        next.copy(restCheckpoint = ActiveRestCheckpoint(exerciseId, setIndex, now,
            now + seconds * 1000L, WorkoutResumeState.bootCount(appContext), seconds))
    }

    fun isLiveRestCheckpoint(sessionId: String, checkpoint: ActiveRestCheckpoint): Boolean =
        synchronized(stateLock) { liveRestIdentity == (sessionId to checkpoint.startedElapsedMs) }

    suspend fun advanceCursorDurably(expectedSessionId: String, expectedIndex: Int, skipped: Boolean): Boolean =
        mutateDraftDurably(expectedSessionId) { session ->
            if (expectedIndex !in session.exercises.indices || WorkoutResumeState.cursor(session) != expectedIndex ||
                session.restCheckpoint != null) null
            else WorkoutResumeState.advance(session, expectedIndex, skipped)
        }

    suspend fun replaceRestCheckpoint(expectedSessionId: String, expected: ActiveRestCheckpoint,
        replacement: ActiveRestCheckpoint): Boolean = mutateDraftDurably(expectedSessionId) { session ->
        if (session.restCheckpoint != expected || !WorkoutResumeState.validRest(session, replacement) ||
            replacement.exerciseKey != expected.exerciseKey || replacement.setIndex != expected.setIndex ||
            replacement.startedElapsedMs != expected.startedElapsedMs || replacement.bootCount != expected.bootCount) null
        else session.copy(restCheckpoint = replacement)
    }

    suspend fun closeRestCheckpoint(expectedSessionId: String, expected: ActiveRestCheckpoint,
        observedSeconds: Int?): Boolean = mutateDraftDurably(expectedSessionId) { session ->
        if (session.restCheckpoint != expected || (observedSeconds != null && observedSeconds !in 0..expected.totalSeconds)) return@mutateDraftDurably null
        val set = session.exercises.firstOrNull { it.executionKey == expected.exerciseKey }
            ?.sets?.getOrNull(expected.setIndex)
        if (observedSeconds == null || set == null || !set.completed) session.copy(restCheckpoint = null)
        else editedSet(session, expected.exerciseKey, expected.setIndex, set.weight, set.reps, set.rpe,
            observedSeconds, true, set.repsInReserve)?.copy(restCheckpoint = null)
    }

    private suspend fun mutateDraftDurably(expectedSessionId: String,
        transform: (ActiveSession) -> ActiveSession?): Boolean = commitMutex.withLock {
        val next = synchronized(stateLock) {
            if (committing || savingCriticalSet) return@synchronized null
            val session = _activeSession.value?.takeIf { it.sessionId == expectedSessionId } ?: return@synchronized null
            transform(session)?.also { savingCriticalSet = true }
        } ?: return@withLock false

        try {
            withContext(Dispatchers.IO + NonCancellable) {
                persistJob?.cancelAndJoin()
                persistenceMutex.withLock { dataStore.saveActiveSessionJson(gson.toJson(next)) }
                synchronized(stateLock) { _activeSession.value = next }
            }
            true
        } finally {
            synchronized(stateLock) {
                savingCriticalSet = false
                val queued = pendingRests.toList()
                pendingRests.clear()
                queued.forEach { rest ->
                    commitRestTaken(rest.key, rest.index, rest.seconds, rest.sessionId)
                }
            }
        }
    }

    /**
     * Adds an extra set to the given exercise in-memory.
     */
    private fun withCustomSet(session: ActiveSession, exerciseId: String): ActiveSession? {
        val exercise = session.exercises.find { it.executionKey == exerciseId } ?: return null
        if (exercise.sets.size >= 20) return null
        val lastSet = exercise.sets.lastOrNull()
        val updated = exercise.copy(sets = (exercise.sets + ActiveSet(
            setNumber = (exercise.sets.maxOfOrNull { it.setNumber } ?: 0) + 1,
            weight = lastSet?.weight ?: 50.0,
            reps = lastSet?.reps ?: 10,
            rpe = lastSet?.rpe?.takeIf { it in 1..10 } ?: 0,
            weightUnit = lastSet?.weightUnit ?: "kg"
        )).toMutableList())
        return session.copy(exercises = session.exercises.map {
            if (it.executionKey == exerciseId) updated else it
        }.toMutableList())
    }

    fun addCustomSet(exerciseId: String) {
        synchronized(stateLock) {
            if (committing || savingCriticalSet) return
            val session = _activeSession.value ?: return
            _activeSession.value = withCustomSet(session, exerciseId) ?: return
            persistSession(immediate = true)
        }
    }

    suspend fun addCustomSetDurably(expectedSessionId: String, exerciseId: String): Boolean =
        mutateDraftDurably(expectedSessionId) { session -> withCustomSet(session, exerciseId) }

    fun undoSetVolumeAdjustment(exerciseId: String): Boolean {
        synchronized(stateLock) {
        if (committing || savingCriticalSet) return false
        val session = _activeSession.value ?: return false
        val exercise = session.exercises.firstOrNull { it.executionKey == exerciseId } ?: return false
        val sets = exercise.volumeAdjustment?.revert(exercise.sets) ?: return false
        val restored = exercise.copy(sets = sets.toMutableList(), volumeAdjustment = null)
        val updated = session.copy(exercises = session.exercises.map {
            if (it === exercise) restored else it
        }.toMutableList())
        if (!_activeSession.compareAndSet(session, updated)) return false
        persistSession(immediate = true)
        return true
        }
    }

    // Called under stateLock by mutation methods. Serialized writes prevent a
    // delayed draft from replacing the final snapshot or a later workout.
    private fun persistSession(immediate: Boolean = false) {
        persistJob?.cancel()
        val session = _activeSession.value
        persistJob = scope.launch {
            if (!immediate) delay(500)
            try {
                persistenceMutex.withLock {
                    dataStore.saveActiveSessionJson(session?.let { gson.toJson(it) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("WorkoutSessionManager", "Session persist failed", e)
            }
        }
    }

    private fun resetActiveState() {
        lastProgressionResults.clear()
        liveRestIdentity = null
        _activeSession.value = null
        _lastWeights.value = emptyMap()
        _weightContextLines.value = emptyMap()
        _pendingPRWarnings.value = emptyMap()
        _restoredFromDisk.value = false
    }

    suspend fun discardDraftDurably(expectedSessionId: String): Boolean = commitMutex.withLock {
        val allowed = synchronized(stateLock) {
            if (committing || savingCriticalSet || _isStartingSession.value || _sessionRestoreFailed.value ||
                _activeSession.value?.sessionId != expectedSessionId) false
            else { committing = true; true }
        }
        if (!allowed) return@withLock false
        try {
            withContext(Dispatchers.IO + NonCancellable) {
                persistJob?.cancelAndJoin()
                persistenceMutex.withLock { dataStore.saveActiveSessionJson(null) }
                synchronized(stateLock) { resetActiveState() }
                try {
                    com.apexfit.app.utils.WorkoutForegroundService.stop(appContext)
                    com.apexfit.app.utils.RestTimerAlarmReceiver.cancelAlarm(appContext)
                } catch (e: Exception) {
                    android.util.Log.e("WorkoutSessionManager", "Draft discarded; service cleanup failed", e)
                }
            }
            true
        } finally {
            synchronized(stateLock) { committing = false }
        }
    }

    fun clearPersistedSession() {
        val id = _activeSession.value?.sessionId ?: return
        scope.launch {
            try {
                discardDraftDurably(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("WorkoutSessionManager", "Draft retained; discard failed", e)
            }
        }
    }

    /**
     * Checks if the given weight/reps would beat the user's all-time record
     * for this exercise. Call this before the user taps complete on a set
     * to show the PR badge in the UI.
     */
    suspend fun checkPRPreview(exerciseId: String, weight: Double, reps: Int) {
        val catalogId = _activeSession.value?.exercises?.firstOrNull { it.executionKey == exerciseId }?.exerciseId ?: return
        val prs = try {
            repository.getPRsForExercise(catalogId)
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
    ): SessionCommitResult {
        val requestedId = _activeSession.value?.sessionId ?: return SessionCommitResult.empty()
        return withContext(Dispatchers.IO) {
        commitMutex.withLock {
        val session = synchronized(stateLock) {
            _activeSession.value?.takeIf { it.sessionId == requestedId }?.also { committing = true }
        } ?: return@withLock lastReceipt?.takeIf { it.sessionId == requestedId } ?: SessionCommitResult.empty()
        try {
        // Finishing is itself an explicit end to any open rest interval.
        // Resolve the checkpoint in the same final snapshot; no separate UI
        // close/write race and no time inferred across a reboot.
        val finalSession = session.restCheckpoint?.let { rest ->
            val observed = WorkoutResumeState.observedRestSeconds(rest, SystemClock.elapsedRealtime(),
                WorkoutResumeState.bootCount(appContext), isLiveRestCheckpoint(session.sessionId, rest))
            val set = session.exercises.firstOrNull { it.executionKey == rest.exerciseKey }?.sets?.getOrNull(rest.setIndex)
            val updated = if (observed != null && set?.completed == true)
                editedSet(session, rest.exerciseKey, rest.setIndex, set.weight, set.reps, set.rpe,
                    observed, true, set.repsInReserve) ?: session
            else session
            updated.copy(restCheckpoint = null)
        } ?: session
        persistJob?.cancelAndJoin()
        // Persist the exact final snapshot before the transaction, so recovery
        // can distinguish a failed commit from a successful commit + failed clear.
        persistenceMutex.withLock { dataStore.saveActiveSessionJson(gson.toJson(finalSession)) }
        synchronized(stateLock) { _activeSession.value = finalSession }

        val sessionId = finalSession.sessionId
        val completedAt = System.currentTimeMillis()
        val durationMinutes = ((completedAt - session.startTime).coerceAtLeast(0L).toDouble() / 60_000.0)
            .let { it.roundToInt() }
            .coerceAtLeast(1)

        // Flatten all sets, filter to completed if needed
        val allSets = finalSession.exercises.flatMapIndexed { occurrenceIndex, exercise ->
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
                        weightUnit = "kg",
                        occurrenceIndex = occurrenceIndex,
                        setNumber = activeSet.setNumber,
                        sourcePlanExerciseId = exercise.sourcePlanExerciseId,
                        setCompletedAt = activeSet.completedAt.takeIf { activeSet.completed && it > 0L }
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

        val partial = isPartial || session.skippedOccurrenceIndices.orEmpty().isNotEmpty() ||
            session.exercises.any { exercise -> exercise.sets.any { !it.completed && !it.isWarmup } }
        val trainingSession = TrainingSession(
            id = sessionId,
            // Attribute a cross-midnight workout to the local calendar day it began.
            date = com.apexfit.app.utils.DateTimeUtils.dateStringForEpochMillis(session.startTime),
            sessionType = session.sessionType,
            completed = completedSetsOnly && !partial,
            durationMinutes = durationMinutes,
            sessionFeel = sessionFeel.coerceIn(1, 5),
            planSessionId = session.planSessionId.toLongOrNull(),
            status = if (partial) "partial" else "completed",
            readinessScoreAtStart = session.readinessScore,
            notes = sessionNotes,
            startedAt = session.startTime,
            completedAt = completedAt
        )

        // Once the durable draft exists, finish the atomic transaction and
        // cleanup even if the caller leaves the screen. PR read failure aborts
        // rather than silently saving inaccurate achievement results.
        withContext(NonCancellable) {
        val db = AppDatabase.getDatabase(appContext)
        val receipt = db.withTransaction {
            check(dao.getTrainingSessionById(sessionId) == null) { "Workout already saved; see workout history." }
            val earned = evaluatePRs(allSets, trainingSession.date)
            // Write the exact earned PR snapshot BEFORE committing Room. If
            // Room rolls back, this staged ID cannot be recovered as a receipt.
            val staged = buildReceipt(trainingSession, allSets, earned).copy(savedSets = emptyList())
            dataStore.stageWorkoutReceipt(sessionId, gson.toJson(staged))
            repository.insertSessionWithPRsAtomic(trainingSession, allSets, earned)
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
            buildReceipt(trainingSession, dao.getSetsForSession(sessionId), earned)
        }

        // A cleanup failure must not be reported as a failed database save.
        // The staged receipt remains until explicit dismissal or a new workout.
        lastReceipt = receipt
        try {
            persistenceMutex.withLock { dataStore.saveActiveSessionJson(null) }
        } catch (e: Exception) {
            android.util.Log.e("WorkoutSessionManager", "Workout saved; cleanup will retry on restore", e)
        }
        synchronized(stateLock) { resetActiveState() }
        try {
            com.apexfit.app.utils.WorkoutForegroundService.stop(appContext)
        } catch (e: Exception) {
            android.util.Log.e("WorkoutSessionManager", "Workout saved; could not stop service", e)
        }
        receipt
        }
        } finally {
            synchronized(stateLock) { committing = false }
        }
        }
        }
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
    fun discardAndExit() = clearPersistedSession()

    val hasCompletedSets: Boolean
        get() = _activeSession.value
            ?.exercises
            ?.flatMap { it.sets }
            ?.any { it.completed }
            ?: false

    // ─────────────────────────────────────────────────────────────
    // PR EVALUATION — runs after commit
    // ─────────────────────────────────────────────────────────────

    private suspend fun evaluatePRs(sets: List<ExerciseSet>, workoutDate: String): List<PersonalRecord> {
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
                val today = workoutDate

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

@androidx.annotation.Keep
data class SessionCommitResult(
    val sessionId: String,
    val durationMinutes: Int,
    val totalVolumeTonnes: Double,  // in tonnes (kg / 1000)
    val setsCompleted: Int,
    val confirmedPRs: List<String>, // list of exerciseIds with new PRs
    val sessionType: String,
    val records: List<com.apexfit.app.data.PersonalRecord> = emptyList(),
    val isPartial: Boolean = false,
    val savedSets: List<ExerciseSet> = emptyList()
) {
    companion object {
        fun empty() = SessionCommitResult("", 0, 0.0, 0, emptyList(), "")
    }
}

private val com.apexfit.app.data.ActiveSession.sessionName: String get() = this.sessionType
