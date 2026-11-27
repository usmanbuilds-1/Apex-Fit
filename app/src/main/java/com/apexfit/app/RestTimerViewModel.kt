package com.apexfit.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexfit.app.data.ActiveExercise
import com.apexfit.app.data.ActiveRestCheckpoint
import com.apexfit.app.data.ActiveSession
import com.apexfit.app.utils.AudioService
import com.apexfit.app.utils.WorkoutResumeState
import com.apexfit.app.utils.RestTimerAlarmReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private var timerJob: Job? = null
    private var sessionId: String? = null
    private var checkpoint: ActiveRestCheckpoint? = null
    private var writing = false

    private val _restTimerSeconds = MutableStateFlow(0)
    val restTimerSeconds: StateFlow<Int> = _restTimerSeconds.asStateFlow()
    private val _restTimerTotal = MutableStateFlow(0)
    val restTimerTotal: StateFlow<Int> = _restTimerTotal.asStateFlow()
    private val _isRestTimerActive = MutableStateFlow(false)
    val isRestTimerActive: StateFlow<Boolean> = _isRestTimerActive.asStateFlow()
    private val _showRestOverlay = MutableStateFlow(false)
    val showRestOverlay: StateFlow<Boolean> = _showRestOverlay.asStateFlow()
    private val _restTimerLastSetContext = MutableStateFlow("")
    val restTimerLastSetContext: StateFlow<String> = _restTimerLastSetContext.asStateFlow()
    private val _restTimerNextSetPreview = MutableStateFlow("")
    val restTimerNextSetPreview: StateFlow<String> = _restTimerNextSetPreview.asStateFlow()

    init {
        // Room/DataStore recovery is the authority. UI callbacks never start
        // another independent countdown or recreate a cleared checkpoint.
        viewModelScope.launch {
            sessionManager.activeSession
                .map { it?.sessionId to it?.restCheckpoint }
                .distinctUntilChanged()
                .collect { (id, saved) ->
                    val active = sessionManager.activeSession.value
                    if (id == null || active?.sessionId != id || saved == null) {
                        stopLocal()
                    } else {
                        installCheckpoint(active, saved)
                    }
                }
        }
    }

    // Compatibility entry point used by the existing RIR screen. A checkpoint
    // was written atomically with the completed set; the collector installs it.
    fun triggerRestTimer(
        rpe: Int, exerciseName: String, muscleGroup: String, reps: Int,
        setIndex: Int, totalSets: Int, weight: Double? = null,
        units: String = "kg", currentExerciseIdx: Int = 0,
        completedExerciseId: String? = null
    ) {
        val active = sessionManager.activeSession.value ?: return
        val saved = active.restCheckpoint ?: return
        if (saved.setIndex == setIndex &&
            (completedExerciseId == null || completedExerciseId == saved.exerciseKey) &&
            (sessionId != active.sessionId || checkpoint != saved)) {
            installCheckpoint(active, saved)
        }
    }

    private fun installCheckpoint(active: ActiveSession, saved: ActiveRestCheckpoint) {
        if (sessionId == active.sessionId && checkpoint == saved && _isRestTimerActive.value) return
        timerJob?.cancel()
        sessionId = active.sessionId
        checkpoint = saved
        _restTimerTotal.value = saved.totalSeconds
        _showRestOverlay.value = true
        _isRestTimerActive.value = true
        updateContext(active, saved)
        val now = SystemClock.elapsedRealtime()
        val boot = WorkoutResumeState.bootCount(getApplication())
        val valid = WorkoutResumeState.validRest(active, saved) && WorkoutResumeState.sameBoot(saved, now, boot, sessionManager.isLiveRestCheckpoint(active.sessionId, saved))
        if (!valid) {
            _restTimerSeconds.value = 0
            _restTimerLastSetContext.value = "Rest timing unavailable after restart. Close to continue."
            // Do not invent time from wall-clock or another boot.
            finishCheckpoint(active.sessionId, saved, null)
            return
        }
        val remaining = remainingRestSeconds(saved.deadlineElapsedMs, now)
        _restTimerSeconds.value = remaining
        if (remaining == 0) {
            finishCheckpoint(active.sessionId, saved, WorkoutResumeState.observedRestSeconds(saved, now, boot, sessionManager.isLiveRestCheckpoint(active.sessionId, saved)))
            return
        }
        RestTimerAlarmReceiver.scheduleAlarm(getApplication(),
            System.currentTimeMillis() + remaining * 1000L, restExerciseName(active, saved))
        timerJob = viewModelScope.launch {
            while (sessionId == active.sessionId && checkpoint == saved && _isRestTimerActive.value) {
                val tick = SystemClock.elapsedRealtime()
                val previous = _restTimerSeconds.value
                val seconds = remainingRestSeconds(saved.deadlineElapsedMs, tick)
                _restTimerSeconds.value = seconds
                if (seconds != previous && seconds in 1..3) AudioService.playBeep(getApplication())
                if (seconds == 0) {
                    if (previous > 0) AudioService.playRestTimerComplete(getApplication())
                    finishCheckpoint(active.sessionId, saved, WorkoutResumeState.observedRestSeconds(saved, tick, boot, sessionManager.isLiveRestCheckpoint(active.sessionId, saved)))
                    break
                }
                delay(250)
            }
        }
    }

    private fun restExerciseName(active: ActiveSession, saved: ActiveRestCheckpoint): String =
        active.exercises.firstOrNull { it.executionKey == saved.exerciseKey }?.exerciseName ?: "Rest"

    private fun updateContext(active: ActiveSession, saved: ActiveRestCheckpoint) {
        val ex = active.exercises.firstOrNull { it.executionKey == saved.exerciseKey }
        val set = ex?.sets?.getOrNull(saved.setIndex)
        _restTimerLastSetContext.value = set?.let {
            "Last set: ${it.weight} ${it.weightUnit} × ${it.reps} reps · RPE ${it.rpe}"
        } ?: "Last set unavailable"
        val next = active.exercises.getOrNull(WorkoutResumeState.cursor(active))
        val nextIndex = next?.sets?.indexOfFirst { !it.completed && !it.isWarmup } ?: -1
        _restTimerNextSetPreview.value = if (next != null && nextIndex >= 0)
            "Next: ${nextSetSummary(next, nextIndex)}" else "Next: Review workout"
    }

    private fun nextSetSummary(exercise: ActiveExercise, setIndex: Int): String {
        val set = exercise.sets[setIndex]
        val load = if (set.weight > 0.0) "${set.weight} ${set.weightUnit}" else "Load not set"
        val reps = if ((exercise.targetRepsMin ?: 0) > 0 && (exercise.targetRepsMax ?: 0) > 0)
            "${exercise.targetRepsMin}–${exercise.targetRepsMax}" else set.reps.toString()
        return "${exercise.exerciseName} · Set ${setIndex + 1} of ${exercise.sets.size} · $load × $reps"
    }

    private fun stopLocal() {
        timerJob?.cancel()
        timerJob = null
        checkpoint = null
        sessionId = null
        _isRestTimerActive.value = false
        _showRestOverlay.value = false
        _restTimerSeconds.value = 0
        sessionManager.resetRestSeconds()
        RestTimerAlarmReceiver.cancelAlarm(getApplication())
    }

    private fun finishCheckpoint(id: String, saved: ActiveRestCheckpoint, observed: Int?) {
        if (writing) return
        writing = true
        viewModelScope.launch {
            try {
                if (sessionManager.closeRestCheckpoint(id, saved, observed) && sessionId == id && checkpoint == saved) stopLocal()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _restTimerLastSetContext.value = "Rest could not be saved. Close again to retry."
            } finally {
                writing = false
            }
        }
    }

    private fun adjust(id: String, saved: ActiveRestCheckpoint, replacement: ActiveRestCheckpoint) {
        if (writing) return
        writing = true
        viewModelScope.launch {
            try {
                if (sessionManager.replaceRestCheckpoint(id, saved, replacement)) {
                    sessionManager.activeSession.value?.takeIf { it.sessionId == id }
                        ?.let { installCheckpoint(it, replacement) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _restTimerLastSetContext.value = "Rest adjustment could not be saved. Try again."
            } finally {
                writing = false
                // The timer might expire during the durable adjustment.
                val current = checkpoint
                val currentId = sessionId
                if (current != null && currentId != null && current.deadlineElapsedMs <= SystemClock.elapsedRealtime()) {
                    finishCheckpoint(currentId, current, WorkoutResumeState.observedRestSeconds(
                        current, SystemClock.elapsedRealtime(), WorkoutResumeState.bootCount(getApplication()), sessionManager.isLiveRestCheckpoint(currentId, current)))
                }
            }
        }
    }

    fun addRestTimerSeconds(secs: Int) {
        val saved = checkpoint ?: return
        val id = sessionId ?: return
        if (writing) return
        val now = SystemClock.elapsedRealtime()
        val elapsed = elapsedRestSeconds(saved.startedElapsedMs, now)
        val total = (saved.totalSeconds.toLong() + secs).coerceIn(1L, 3600L).toInt()
        if (total <= elapsed) {
            closeRestTimer()
            return
        }
        adjust(id, saved, saved.copy(totalSeconds = total,
            deadlineElapsedMs = saved.startedElapsedMs + total * 1000L,
            adjustmentSeconds = saved.adjustmentSeconds + total - saved.totalSeconds))
    }

    fun updateRestTimerForRpe(rpe: Int, exerciseName: String, muscleGroup: String,
        exerciseId: String, weight: Double, units: String, reps: Int) {
        val saved = checkpoint ?: return
        val id = sessionId ?: return
        if (writing || saved.exerciseKey != exerciseId) return
        sessionManager.activeSession.value?.let { updateContext(it, saved) }
        val type = com.apexfit.app.utils.ProgressionEngine.getExerciseType(exerciseName, muscleGroup)
        val total = (sessionManager.calculateRestTimeSeconds(exerciseId, type, rpe) +
            saved.adjustmentSeconds).coerceIn(1, 3600)
        val elapsed = elapsedRestSeconds(saved.startedElapsedMs, SystemClock.elapsedRealtime())
        if (total <= elapsed) { closeRestTimer(); return }
        adjust(id, saved, saved.copy(totalSeconds = total, deadlineElapsedMs = saved.startedElapsedMs + total * 1000L))
    }

    fun closeRestTimer(lastCompletedSetPointer: Pair<String, Int>? = null) {
        val saved = checkpoint ?: return
        val id = sessionId ?: return
        finishCheckpoint(id, saved, WorkoutResumeState.observedRestSeconds(saved,
            SystemClock.elapsedRealtime(), WorkoutResumeState.bootCount(getApplication()), sessionManager.isLiveRestCheckpoint(id, saved)))
    }

    fun cancelRestTimer() {
        // If the workout is being discarded its checkpoint disappears with it.
        // Otherwise close through the same durable observation path.
        if (sessionManager.activeSession.value == null) stopLocal() else closeRestTimer()
    }

    fun clearClosedRestTimerContext() {
        if (_isRestTimerActive.value) return
        _restTimerSeconds.value = 0
        _restTimerTotal.value = 0
        _restTimerLastSetContext.value = ""
        _restTimerNextSetPreview.value = ""
    }
}
