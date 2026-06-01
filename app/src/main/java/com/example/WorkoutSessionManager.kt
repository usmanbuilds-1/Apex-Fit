package com.example

import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Owns all in-memory workout state. A single source of truth for the
 * active session from first tap to session complete.
 *
 * Nothing touches Room until commitToDatabase() is called — one atomic
 * write covers the TrainingSession row and all ExerciseSet rows together.
 */
class WorkoutSessionManager(private val dao: FitnessDao) {

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

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val userBodyWeightLbs = if (preferredUnits.lowercase() == "lbs") {
            userWeight
        } else {
            userWeight * 2.205
        }

        exercises.forEach { ex ->
            val lastSet = dao.getLastSetForExercise(ex.id.toString())
            val exType = com.example.utils.ProgressionEngine.getExerciseType(ex.name, ex.muscleGroup)

            val suggestedLbs: Double
            if (lastSet == null) {
                // First-time or beginner starting weight
                suggestedLbs = com.example.utils.ProgressionEngine.calculateBeginnerStartingWeight(
                    exerciseType = exType,
                    userBodyWeightLbs = userBodyWeightLbs,
                    userHeightCm = userHeight
                )

                val suggestedPreferred = if (preferredUnits.lowercase() == "lbs") {
                    suggestedLbs
                } else {
                    val converted = suggestedLbs / 2.205
                    com.example.utils.ProgressionEngine.run { converted.roundToNearest2_5() }
                }

                suggestionsMap[ex.id.toString()] = suggestedPreferred
                lastWeightMap[ex.id.toString()] = suggestedPreferred
                contextLinesMap[ex.id.toString()] = "First session suggestion (Beginner Base): $suggestedPreferred $preferredUnits"
            } else {
                val daysSince = com.example.utils.ProgressionEngine.getDaysBetween(lastSet.date, today)

                val lastWeightLbs = if (preferredUnits.lowercase() == "lbs") {
                    lastSet.weight
                } else {
                    lastSet.weight * 2.205
                }

                suggestedLbs = com.example.utils.ProgressionEngine.calculateProgressiveWeight(
                    lastWeight = lastWeightLbs,
                    lastRPE = lastSet.rpe,
                    daysSinceLastSession = daysSince,
                    userBodyWeightLbs = userBodyWeightLbs,
                    exerciseType = exType
                )

                val suggestedPreferred = if (preferredUnits.lowercase() == "lbs") {
                    suggestedLbs
                } else {
                    val converted = suggestedLbs / 2.205
                    com.example.utils.ProgressionEngine.run { converted.roundToNearest2_5() }
                }

                suggestionsMap[ex.id.toString()] = suggestedPreferred
                lastWeightMap[ex.id.toString()] = lastSet.weight
                contextLinesMap[ex.id.toString()] = "Last: ${lastSet.weight} $preferredUnits @ RPE ${lastSet.rpe} ($daysSince days ago) → Suggested: $suggestedPreferred $preferredUnits"
            }
        }

        _lastWeights.value = lastWeightMap
        _weightContextLines.value = contextLinesMap

        // Build the in-memory session with pre-filled sets
        val activeExercises = exercises.map { ex ->
            val suggestedWeight = suggestionsMap[ex.id.toString()] ?: ex.weight
            ActiveExercise(
                exerciseId = ex.id.toString(),
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
        val exercise = session.exercises.find { it.exerciseId == exerciseId } ?: return

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
            // Trigger StateFlow emission with new object reference
            _activeSession.value = session.copy()
        }
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
        _activeSession.value = session.copy()
    }


    /**
     * Checks if the given weight/reps would beat the user's all-time record
     * for this exercise. Call this before the user taps complete on a set
     * to show the PR badge in the UI.
     */
    suspend fun checkPRPreview(exerciseId: String, weight: Double, reps: Int) {
        val prs = dao.getPRsForExercise(exerciseId)
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
            date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
            sessionType = session.sessionType,
            completed = true,
            durationMinutes = durationMinutes,
            sessionFeel = sessionFeel.coerceIn(1, 5)
        )

        // Single atomic transaction — both rows or neither
        dao.insertSessionAtomic(trainingSession, allSets)

        // Save PRs after commit
        evaluateAndSavePRs(allSets)

        val totalVolume = allSets
            .filter { !it.isWarmup }
            .sumOf { it.weight * it.reps }

        val confirmedPRs = _pendingPRWarnings.value.keys.toList()

        // Clear state
        _activeSession.value = null
        _lastWeights.value = emptyMap()
        _weightContextLines.value = emptyMap()
        _pendingPRWarnings.value = emptyMap()

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
        _activeSession.value = null
        _lastWeights.value = emptyMap()
        _weightContextLines.value = emptyMap()
        _pendingPRWarnings.value = emptyMap()
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

    private suspend fun evaluateAndSavePRs(sets: List<ExerciseSet>) {
        sets.filter { !it.isWarmup && it.completed }
            .groupBy { it.exerciseId }
            .forEach { (exerciseId, exerciseSets) ->

                val maxWeight = exerciseSets.maxOfOrNull { it.weight } ?: return@forEach
                val totalVolume = exerciseSets.sumOf { it.weight * it.reps }
                val maxEstimated1RM = exerciseSets.maxOfOrNull {
                    it.weight * (1.0 + it.reps / 30.0)
                } ?: return@forEach

                val existing = dao.getPRsForExercise(exerciseId)
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

                val prevWeight = existing.firstOrNull { it.type == "max_weight" }?.value ?: 0.0
                val prevVolume = existing.firstOrNull { it.type == "volume" }?.value ?: 0.0
                val prev1RM = existing.firstOrNull { it.type == "estimated_1rm" }?.value ?: 0.0

                if (maxWeight > prevWeight) {
                    dao.insertPersonalRecord(
                        PersonalRecord("${exerciseId}_max_weight", exerciseId, "max_weight", maxWeight, today)
                    )
                }
                if (totalVolume > prevVolume) {
                    dao.insertPersonalRecord(
                        PersonalRecord("${exerciseId}_volume", exerciseId, "volume", totalVolume, today)
                    )
                }
                if (maxEstimated1RM > prev1RM) {
                    dao.insertPersonalRecord(
                        PersonalRecord("${exerciseId}_estimated_1rm", exerciseId, "estimated_1rm", maxEstimated1RM, today)
                    )
                }
            }
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
