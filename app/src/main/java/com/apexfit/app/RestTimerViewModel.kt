package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.data.FitnessDao
import com.apexfit.app.data.repository.FitnessRepositoryImpl
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.utils.AudioService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RestTimerViewModel @Inject constructor(
    application: Application,
    private val db: AppDatabase = AppDatabase.getDatabase(application),
    private val dao: FitnessDao = db.fitnessDao(),
    private val dataStore: DataStoreManager = DataStoreManager.getInstance(application),
    private val repository: FitnessRepository = FitnessRepositoryImpl(db, dao, dataStore),
    val sessionManager: WorkoutSessionManager = WorkoutSessionManager(
        repository = repository,
        dataStore = dataStore,
        scope = repository.appScope,
        dao = dao,
        appContext = application
    )
) : AndroidViewModel(application) {

    private var restTimerJob: Job? = null

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
        currentExerciseIdx: Int = 0
    ) {
        val exerciseType = com.apexfit.app.utils.ProgressionEngine.getExerciseType(exerciseName, muscleGroup)
        val exerciseId = com.apexfit.app.utils.exerciseNameToSlug(exerciseName)
        val restSeconds = sessionManager.calculateRestTimeSeconds(exerciseId, exerciseType, rpe)

        _restTimerLastSetContext.value = "Last set: $reps reps @ RPE $rpe"

        val activeExercises = sessionManager.activeSession.value?.exercises ?: emptyList()
        val nextPreview = if (setIndex + 1 < totalSets) {
            "$exerciseName (set ${setIndex + 2}/$totalSets)"
        } else {
            val nextIdx = currentExerciseIdx + 1
            if (nextIdx < activeExercises.size) {
                "${activeExercises[nextIdx].exerciseName} (set 1/${activeExercises[nextIdx].sets.size})"
            } else {
                "Complete Workout"
            }
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
        startLocalTimer(restSeconds, exerciseName)
    }

    private fun startLocalTimer(durationSeconds: Int, exerciseName: String) {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            var remaining = durationSeconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                val prev = _restTimerSeconds.value
                _restTimerSeconds.value = remaining

                sessionManager.incrementRestSeconds()

                if (remaining in 1..3) {
                    AudioService.playBeep(getApplication())
                } else if (remaining == 0 && prev > 0) {
                    AudioService.playRestTimerComplete(getApplication())
                }
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
        val newSeconds = _restTimerSeconds.value + secs
        _restTimerTotal.value += secs
        _restTimerSeconds.value = newSeconds

        // Reschedule alarm for the new duration
        val triggerAtMillis = System.currentTimeMillis() + (newSeconds * 1000L)
        com.apexfit.app.utils.RestTimerAlarmReceiver.scheduleAlarm(
            context = getApplication(),
            triggerAtMillis = triggerAtMillis,
            exerciseName = "Rest"
        )

        // Restart local timer from the new duration
        startLocalTimer(newSeconds, "Rest")
    }

    fun closeRestTimer(lastCompletedSetPointer: Pair<String, Int>? = null) {
        _isRestTimerActive.value = false
        _showRestOverlay.value = false
        restTimerJob?.cancel()
        // Commit the elapsed rest time to the last completed set
        val pointer = lastCompletedSetPointer ?: findLastCompletedSetPointer()
        pointer?.let { (exId, sIdx) ->
            sessionManager.commitRestTaken(exId, sIdx, sessionManager.currentRestSeconds.value)
        }
        sessionManager.resetRestSeconds()
        // Cancel the pending alarm — user dismissed the timer manually
        com.apexfit.app.utils.RestTimerAlarmReceiver.cancelAlarm(getApplication())
    }

    fun cancelRestTimer() {
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
                    lastPointer = ex.exerciseId to idx
                }
            }
        }
        return lastPointer
    }
}
