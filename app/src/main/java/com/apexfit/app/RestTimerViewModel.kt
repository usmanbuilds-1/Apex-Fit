package com.apexfit.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexfit.app.utils.AudioService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal fun remainingRestSeconds(deadline: Long, now: Long): Int =
    ((deadline - now).coerceAtLeast(0L) + 999L).div(1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun elapsedRestSeconds(startedAt: Long, now: Long): Int =
    ((now - startedAt).coerceAtLeast(0L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

@HiltViewModel
class RestTimerViewModel @Inject constructor(
    application: Application,
    val sessionManager: WorkoutSessionManager
) : AndroidViewModel(application) {

    private var restTimerJob: Job? = null
    private var restStartedAt = 0L
    private var restDeadline = 0L
    private var restExerciseName = "Rest"
    private var restSetPointer: Pair<String, Int>? = null
    private var restSessionId: String? = null
    private var restAdjustmentSeconds = 0

    // Rest Timer State
    private val _restTimerSeconds = MutableStateFlow(0)
    val restTimerSeconds: StateFlow<Int> = _restTimerSeconds.asStateFlow()

    private val _restTimerTotal = MutableStateFlow(1)
    val restTimerTotal: StateFlow<Int> = _restTimerTotal.asStateFlow()

    private val _isRestTimerActive = MutableStateFlow(false)
    val isRestTimerActive: StateFlow<Boolean> = _isRestTimerActive.asStateFlow()

    private val _showRestOverlay = MutableStateFlow(false)
    val showRestOverlay: StateFlow<Boolean> = _showRestOverlay.asStateFlow()

    private val _restTimerLastSetContext = MutableStateFlow("")
    val restTimerLastSetContext: StateFlow<String> = _restTimerLastSetContext.asStateFlow()

    private val _restTimerNextSetPreview = MutableStateFlow("")
    val restTimerNextSetPreview: StateFlow<String> = _restTimerNextSetPreview.asStateFlow()

    fun triggerRestTimer(
        rpe: Int,
        exerciseName: String,
        muscleGroup: String,
        reps: Int,
        setIndex: Int,
        totalSets: Int,
        weight: Double? = null,
        units: String = "kg",
        currentExerciseIdx: Int = 0,
        completedExerciseId: String? = null
    ) {
        val exerciseType = com.apexfit.app.utils.ProgressionEngine.getExerciseType(exerciseName, muscleGroup)
        val exerciseId = completedExerciseId?.takeIf { it.isNotBlank() }
            ?: com.apexfit.app.utils.exerciseNameToSlug(exerciseName)
        val restSeconds = sessionManager.calculateRestTimeSeconds(exerciseId, exerciseType, rpe)
        restAdjustmentSeconds = 0

        _restTimerLastSetContext.value = if (weight != null) {
            "Last set: $weight $units × $reps reps · RPE $rpe"
        } else {
            "Last set: $reps reps @ RPE $rpe"
        }

        val active = sessionManager.activeSession.value
        val activeExercises = active?.exercises ?: emptyList()
        restSessionId = active?.sessionId
        restSetPointer = activeExercises.firstOrNull { it.executionKey == exerciseId }?.let { it.executionKey to setIndex }
        restExerciseName = exerciseName
        restStartedAt = SystemClock.elapsedRealtime()
        restDeadline = restStartedAt + restSeconds * 1000L
        val exerciseIndex = activeExercises.indexOfFirst { it.executionKey == exerciseId }
        val completedExercise = activeExercises.getOrNull(exerciseIndex)
        val nextIncomplete = completedExercise?.sets?.indexOfFirst { !it.completed } ?: -1
        val nextPreview = when {
            nextIncomplete >= 0 -> nextSetSummary(completedExercise!!, nextIncomplete)
            exerciseIndex >= 0 && exerciseIndex + 1 < activeExercises.size -> {
                val next = activeExercises[exerciseIndex + 1]
                nextSetSummary(next, 0)
            }
            else -> "Review workout"
        }
        _restTimerNextSetPreview.value = "Next: $nextPreview"

        sessionManager.resetRestSeconds()
        _restTimerTotal.value = restSeconds
        _restTimerSeconds.value = restSeconds
        _showRestOverlay.value = true
        _isRestTimerActive.value = true

        // Schedule a one-time alarm for when the timer completes (background-safe)
        val triggerAtMillis = System.currentTimeMillis() + (restSeconds * 1000L)
        com.apexfit.app.utils.RestTimerAlarmReceiver.scheduleAlarm(
            context = getApplication(),
            triggerAtMillis = triggerAtMillis,
            exerciseName = exerciseName
        )

        // Start local coroutine countdown
        startLocalTimer()
    }

    private fun nextSetSummary(exercise: com.apexfit.app.data.ActiveExercise, setIndex: Int): String {
        val set = exercise.sets.getOrNull(setIndex) ?: return "${exercise.exerciseName} · Set ${setIndex + 1}"
        val load = if (set.weight > 0.0) {
            val weightText = if (set.weight % 1.0 == 0.0) set.weight.toLong().toString() else set.weight.toString()
            "$weightText ${set.weightUnit}"
        } else "Load not set"
        val repRange = if ((exercise.targetRepsMin ?: 0) > 0 && (exercise.targetRepsMax ?: 0) > 0) {
            "${exercise.targetRepsMin}–${exercise.targetRepsMax}"
        } else set.reps.toString()
        return "${exercise.exerciseName} · Set ${setIndex + 1} of ${exercise.sets.size} · $load × $repRange"
    }

    private fun startLocalTimer() {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            while (_isRestTimerActive.value) {
                val remaining = remainingRestSeconds(restDeadline, SystemClock.elapsedRealtime())
                val prev = _restTimerSeconds.value
                _restTimerSeconds.value = remaining
                if (remaining != prev && remaining in 1..3) {
                    AudioService.playBeep(getApplication())
                } else if (remaining == 0 && prev > 0) {
                    AudioService.playRestTimerComplete(getApplication())
                }
                if (remaining == 0) break
                delay(250)
            }

            if (_isRestTimerActive.value) {
                // Timer completed naturally — cancel the alarm first so it doesn't fire
                // a duplicate notification after the in-app completion already played
                com.apexfit.app.utils.RestTimerAlarmReceiver.cancelAlarm(getApplication())
                closeRestTimer()
            }
        }
    }

    fun addRestTimerSeconds(secs: Int) {
        if (!_isRestTimerActive.value) return
        val now = SystemClock.elapsedRealtime()
        val remaining = remainingRestSeconds(restDeadline, now)
        val newSeconds = (remaining + secs).coerceAtLeast(0)
        restAdjustmentSeconds += newSeconds - remaining
        _restTimerTotal.value = (_restTimerTotal.value + newSeconds - remaining).coerceAtLeast(1)
        _restTimerSeconds.value = newSeconds
        restDeadline = now + newSeconds * 1000L
        if (newSeconds == 0) {
            closeRestTimer()
            return
        }

        // Reschedule alarm for the new duration
        val triggerAtMillis = System.currentTimeMillis() + (newSeconds * 1000L)
        com.apexfit.app.utils.RestTimerAlarmReceiver.scheduleAlarm(
            context = getApplication(),
            triggerAtMillis = triggerAtMillis,
            exerciseName = restExerciseName
        )

        // Restart local timer from the new duration
        startLocalTimer()
    }

    fun clearClosedRestTimerContext() {
        if (_isRestTimerActive.value) return
        _restTimerSeconds.value = 0
        _restTimerTotal.value = 0
        _restTimerLastSetContext.value = ""
        _restTimerNextSetPreview.value = ""
    }

    fun updateRestTimerForRpe(
        rpe: Int,
        exerciseName: String,
        muscleGroup: String,
        exerciseId: String,
        weight: Double,
        units: String,
        reps: Int
    ) {
        _restTimerLastSetContext.value = "Last set: $weight $units × $reps reps · RPE $rpe"
        if (!_isRestTimerActive.value) return
        val now = SystemClock.elapsedRealtime()
        val elapsed = elapsedRestSeconds(restStartedAt, now)
        val exerciseType = com.apexfit.app.utils.ProgressionEngine.getExerciseType(exerciseName, muscleGroup)
        val baseSeconds = sessionManager.calculateRestTimeSeconds(exerciseId, exerciseType, rpe)
        val newTotal = (baseSeconds + restAdjustmentSeconds).coerceAtLeast(1)
        val newRemaining = (newTotal - elapsed).coerceAtLeast(0)
        _restTimerTotal.value = newTotal
        _restTimerSeconds.value = newRemaining
        restDeadline = now + newRemaining * 1000L
        if (newRemaining == 0) {
            closeRestTimer()
            return
        }
        val triggerAtMillis = System.currentTimeMillis() + newRemaining * 1000L
        com.apexfit.app.utils.RestTimerAlarmReceiver.scheduleAlarm(
            context = getApplication(),
            triggerAtMillis = triggerAtMillis,
            exerciseName = restExerciseName
        )
        startLocalTimer()
    }

    fun closeRestTimer(lastCompletedSetPointer: Pair<String, Int>? = null) {
        if (!_isRestTimerActive.value) return
        _isRestTimerActive.value = false
        _showRestOverlay.value = false
        restTimerJob?.cancel()
        // Commit the elapsed rest time to the last completed set
        val pointer = if (restSessionId != null && restSessionId == sessionManager.activeSession.value?.sessionId) {
            restSetPointer ?: lastCompletedSetPointer
        } else null
        pointer?.let { (exId, sIdx) ->
            val elapsed = elapsedRestSeconds(restStartedAt, SystemClock.elapsedRealtime())
            sessionManager.commitRestTaken(exId, sIdx, elapsed, expectedSessionId = restSessionId)
        }
        restSetPointer = null
        restSessionId = null
        restAdjustmentSeconds = 0
        sessionManager.resetRestSeconds()
        // Cancel the pending alarm — user dismissed the timer manually
        com.apexfit.app.utils.RestTimerAlarmReceiver.cancelAlarm(getApplication())
    }

    fun cancelRestTimer() {
        restSetPointer = null
        restSessionId = null
        restTimerJob?.cancel()
        restTimerJob = null
        _isRestTimerActive.value = false
        _showRestOverlay.value = false
        _restTimerSeconds.value = 0
        com.apexfit.app.utils.RestTimerAlarmReceiver.cancelAlarm(getApplication())
    }

    private fun findLastCompletedSetPointer(): Pair<String, Int>? {
        val active = sessionManager.activeSession.value ?: return null
        var lastPointer: Pair<String, Int>? = null
        active.exercises.forEach { ex ->
            ex.sets.forEachIndexed { idx, set ->
                if (set.completed) {
                    lastPointer = ex.executionKey to idx
                }
            }
        }
        return lastPointer
    }
}
