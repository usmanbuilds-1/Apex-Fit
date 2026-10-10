@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.apexfit.app.data.*
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.ui.models.*
import com.apexfit.app.utils.AppConstants
import com.apexfit.app.utils.PatternScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WorkoutExecutionViewModel @Inject constructor(
    application: Application,
    private val repository: FitnessRepository,
    private val dataStore: DataStoreManager,
    private val dao: FitnessDao,
    val sessionManager: WorkoutSessionManager
) : AndroidViewModel(application) {

    private val isCommitting = java.util.concurrent.atomic.AtomicBoolean(false)

    // User weight from preferences for relative calculations
    val currentWeight = dataStore.currentWeightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.apexfit.app.UserDefaults.WEIGHT_KG)
    val userHeight = dataStore.heightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.apexfit.app.UserDefaults.HEIGHT_CM)
    val units = dataStore.unitsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "kg")

    // Active Workout Mode state (derived reactively from sessionManager)
    val activeSession = sessionManager.activeSession
    val lastWeights = sessionManager.lastWeights
    val pendingPRWarnings = sessionManager.pendingPRWarnings

    val activeWorkoutSession: StateFlow<PlanSession?> = sessionManager.activeSession.map { active ->
        if (active != null) {
            PlanSession(
                id = active.planSessionId.toLongOrNull() ?: 0L,
                planId = 0L,
                label = active.sessionType,
                day = "",
                focus = active.sessionType
            )
        } else {
            null
        }
    }.distinctUntilChanged { a, b ->
        a?.id == b?.id && a?.label == b?.label && a?.focus == b?.focus
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeExercises: StateFlow<List<PlanExercise>> = sessionManager.activeSession.map { active ->
        active?.exercises?.map { entry ->
            PlanExercise(
                id = entry.exerciseId.toLongOrNull() ?: 0L,
                planSessionId = active.planSessionId.toLongOrNull() ?: 0L,
                exerciseId = entry.exerciseId,
                name = entry.exerciseName,
                muscleGroup = entry.muscleGroup,
                sets = entry.sets.size,
                repsMin = entry.sets.firstOrNull()?.reps ?: 6,
                repsMax = entry.sets.firstOrNull()?.reps ?: 12,
                weight = entry.sets.firstOrNull()?.weight ?: 0.0,
                restSeconds = 90,
                notes = ""
            )
        } ?: emptyList()
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentExerciseIdx = MutableStateFlow(0)
    val currentExerciseIdx: StateFlow<Int> = _currentExerciseIdx.asStateFlow()

    val loggedSets: StateFlow<Map<String, List<UiExerciseSet>>> = sessionManager.activeSession.map { active ->
        if (active != null) {
            active.exercises.associate { exercise ->
                exercise.exerciseId to exercise.sets.mapIndexed { sIdx, setObj ->
                    ExerciseSet(
                        id = sIdx.toLong(),
                        sessionId = active.planSessionId,
                        exerciseId = exercise.exerciseId,
                        exerciseName = exercise.exerciseName,
                        muscleGroup = exercise.muscleGroup,
                        weight = setObj.weight,
                        reps = setObj.reps,
                        rpe = setObj.rpe,
                        isWarmup = setObj.isWarmup,
                        restTaken = setObj.restTakenSeconds,
                        completed = setObj.completed
                    ).toUi()
                }
            }
        } else {
            emptyMap()
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val weightContextLines: StateFlow<Map<String, String>> = sessionManager.weightContextLines

    val effectiveSetsStateFlow: StateFlow<Map<String, EffectiveSetsData>> =
        sessionManager.activeSession.map { activeSession ->
            if (activeSession == null) return@map emptyMap()
            activeSession.exercises.associate { exercise ->
                val completedSets = exercise.sets.filter { it.completed && !it.isWarmup }
                val currentEff = completedSets.sumOf { setObj ->
                    com.apexfit.app.utils.ProgressionEngine.calculateEffectiveSetValue(setObj.rpe)
                }

                val planSets = exercise.sets.size
                val targetEff = planSets * 0.75

                val lastCompletedSet = completedSets.lastOrNull()
                val lastSetEff = if (lastCompletedSet != null) {
                    com.apexfit.app.utils.ProgressionEngine.calculateEffectiveSetValue(lastCompletedSet.rpe)
                } else {
                    0.0
                }
                val lastSetRpe = lastCompletedSet?.rpe ?: 0
                val prog = if (targetEff > 0) (currentEff / targetEff) * 100.0 else 0.0

                exercise.exerciseId to EffectiveSetsData(
                    exerciseId = exercise.exerciseId,
                    currentEffectiveSets = currentEff,
                    targetEffectiveSets = targetEff,
                    lastSetEffectiveness = lastSetEff,
                    lastSetRPE = lastSetRpe,
                    progress = prog
                )
            }
        }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Warmup Checklist
    private val _warmupCompleted = MutableStateFlow<Map<String, Boolean>>(
        mapOf(
            "Dynamic stretching and joint mobilisations" to false,
            "Arm circles and shoulder rotations" to false,
            "Light cardio or muscle preparation sets (5-10 mins)" to false
        )
    )
    val warmupCompleted: StateFlow<Map<String, Boolean>> = _warmupCompleted.asStateFlow()

    private val _lastCompletedSessionSets = MutableStateFlow<List<UiExerciseSet>>(emptyList())
    val lastCompletedSessionSets: StateFlow<List<UiExerciseSet>> = _lastCompletedSessionSets.asStateFlow()

    // Workout completed page state inside UI
    private val _showSessionCompleteScreen = MutableStateFlow(false)
    val showSessionCompleteScreen: StateFlow<Boolean> = _showSessionCompleteScreen.asStateFlow()

    private val _completedStats = MutableStateFlow<Pair<Int, Double>>(0 to 0.0) // Pair of (DurationMinutes, TotalVolumeWeight)
    val completedStats: StateFlow<Pair<Int, Double>> = _completedStats.asStateFlow()

    private val _completedPRsBroken = MutableStateFlow<List<String>>(emptyList())
    val completedPRsBroken: StateFlow<List<String>> = _completedPRsBroken.asStateFlow()

    // RIR Selector Overlay State
    data class RirOverlayState(
        val isVisible: Boolean = false,
        val exerciseId: String = "",
        val exerciseName: String = "",
        val muscleGroup: String = "",
        val setIndex: Int = 0,
        val weight: Double = 0.0,
        val reps: Int = 0,
        val totalSets: Int = 0,
        val selectedRir: Int = 2,
        val isShowingHistory: Boolean = false,
        val historicalSets: List<com.apexfit.app.data.LastSetWithDate> = emptyList()
    )

    private val _rirOverlayState = MutableStateFlow(RirOverlayState())
    val rirOverlayState: StateFlow<RirOverlayState> = _rirOverlayState.asStateFlow()

    val showRirOverlay: StateFlow<Boolean> = _rirOverlayState.map { it.isVisible }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isShowingRirHistory: StateFlow<Boolean> = _rirOverlayState.map { it.isShowingHistory }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val rirSelectorExerciseId: StateFlow<String> = _rirOverlayState.map { it.exerciseId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val rirSelectorExerciseName: StateFlow<String> = _rirOverlayState.map { it.exerciseName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val rirSelectorMuscleGroup: StateFlow<String> = _rirOverlayState.map { it.muscleGroup }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val rirSelectorSetIndex: StateFlow<Int> = _rirOverlayState.map { it.setIndex }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val rirSelectorWeight: StateFlow<Double> = _rirOverlayState.map { it.weight }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val rirSelectorReps: StateFlow<Int> = _rirOverlayState.map { it.reps }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val rirSelectorTotalSets: StateFlow<Int> = _rirOverlayState.map { it.totalSets }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val selectedRir: StateFlow<Int> = _rirOverlayState.map { it.selectedRir }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    val rirHistoricalSets: StateFlow<List<com.apexfit.app.data.LastSetWithDate>> = _rirOverlayState.map { it.historicalSets }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    fun dismissSaveError() {
        _saveError.value = null
    }

    private val _completedHypertrophyScore = MutableStateFlow(0.0)
    val completedHypertrophyScore: StateFlow<Double> = _completedHypertrophyScore.asStateFlow()

    var lastCompletedSetPointer: Pair<String, Int>? = null
        private set

    fun startWorkoutSession(planSession: PlanSession) {
        _currentExerciseIdx.value = 0
        _showSessionCompleteScreen.value = false
        _warmupCompleted.value = _warmupCompleted.value.mapValues { false }
        lastCompletedSetPointer = null
        // AUDIT FIX (BUG-V4-002): FGS start moved into sessionManager.startSession,
        // which calls it after the session is built and while the app is still
        // foreground. Removed premature start here to prevent orphaned FGS.
        viewModelScope.launch {
            val exercises = dao.getExercisesForSession(planSession.id)
            sessionManager.startSession(
                planSession = planSession,
                exercises = exercises,
                userWeight = currentWeight.value,
                userHeight = userHeight.value,
                preferredUnits = units.value
            )
        }
    }

    fun cancelActiveWorkout() {
        com.apexfit.app.utils.WorkoutForegroundService.stop(getApplication())
        com.apexfit.app.utils.RestTimerAlarmReceiver.cancelAlarm(getApplication())
        sessionManager.discardAndExit()
    }

    fun cancelOrCompleteEmptySession() {
        if (hasCompletedSets) {
            savePartialAndExit(4)
        } else {
            cancelActiveWorkout()
        }
    }

    fun toggleWarmupItem(item: String) {
        val current = _warmupCompleted.value.toMutableMap()
        current[item] = !(current[item] ?: false)
        _warmupCompleted.value = current
    }

    fun logWorkoutSetState(exerciseId: String, setIndex: Int, weight: Double, reps: Int, rpe: Int, completed: Boolean, restTakenSeconds: Int = 0, repsInReserve: Int? = null) {
        if (completed) {
            lastCompletedSetPointer = Pair(exerciseId, setIndex)
            viewModelScope.launch {
                // AUDIT FIX (BUG-V4-010): convert display weight → canonical kg
                // before PR comparison; stored PRs are kg since schema v21.
                val weightKg = if (units.value.lowercase() in listOf("lb", "lbs"))
                    weight / AppConstants.KG_TO_LBS else weight
                sessionManager.checkPRPreview(exerciseId, weightKg, reps)
            }
        }
        sessionManager.updateSet(
            exerciseId = exerciseId,
            setIndex = setIndex,
            weight = weight,
            reps = reps,
            rpe = rpe,
            restTakenSeconds = restTakenSeconds,
            completed = completed,
            repsInReserve = repsInReserve
        )
    }

    fun addCustomLogSet(exerciseId: String) {
        sessionManager.addCustomSet(exerciseId)
    }

    fun openRirSelector(
        exerciseId: String,
        exerciseName: String,
        muscleGroup: String,
        setIndex: Int,
        weight: Double,
        reps: Int,
        totalSets: Int
    ) {
        _rirOverlayState.value = _rirOverlayState.value.copy(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            muscleGroup = muscleGroup,
            setIndex = setIndex,
            weight = weight,
            reps = reps,
            totalSets = totalSets,
            isShowingHistory = false
        )

        viewModelScope.launch {
            val lastSetForEx = dao.getLastSetForExercise(exerciseId)
            val rir = lastSetForEx?.repsInReserve ?: 2
            _rirOverlayState.value = _rirOverlayState.value.copy(
                selectedRir = rir,
                isVisible = true
            )
        }
    }

    fun selectRirOption(rir: Int) {
        _rirOverlayState.value = _rirOverlayState.value.copy(selectedRir = rir)
    }

    fun confirmRirSelection() {
        val exerciseId = _rirOverlayState.value.exerciseId
        val rpe = com.apexfit.app.utils.ProgressionEngine.calculateRPEFromRIR(_rirOverlayState.value.selectedRir)

        viewModelScope.launch {
            val lastSets = dao.getExerciseSetsByExerciseIdAndRPE(exerciseId, rpe, 5)
            _rirOverlayState.value = _rirOverlayState.value.copy(
                historicalSets = lastSets,
                isShowingHistory = true
            )
        }
    }

    fun confirmRirAndNextSet(
        onTriggerRestTimer: ((rpe: Int, exerciseName: String, muscleGroup: String, reps: Int, setIndex: Int, totalSets: Int) -> Unit)? = null
    ) {
        val s = _rirOverlayState.value
        val exId = s.exerciseId
        val exName = s.exerciseName
        val muscleGroup = s.muscleGroup
        val sIdx = s.setIndex
        val w = s.weight
        val r = s.reps
        val totalSets = s.totalSets
        val repsInReserve = s.selectedRir
        val calculatedRpe = com.apexfit.app.utils.ProgressionEngine.calculateRPEFromRIR(repsInReserve)

        logWorkoutSetState(
            exerciseId = exId,
            setIndex = sIdx,
            weight = w,
            reps = r,
            rpe = calculatedRpe,
            restTakenSeconds = 0,
            completed = true,
            repsInReserve = repsInReserve
        )

        _rirOverlayState.value = _rirOverlayState.value.copy(isVisible = false)
        onTriggerRestTimer?.invoke(
            calculatedRpe,
            exName,
            muscleGroup,
            r,
            sIdx,
            totalSets
        )
    }

    fun closeRirSelector() {
        _rirOverlayState.value = _rirOverlayState.value.copy(isVisible = false)
    }

    fun nextExercise() {
        if (_currentExerciseIdx.value < activeExercises.value.size - 1) {
            _currentExerciseIdx.value += 1
        } else {
            finishWorkoutSession()
        }
    }

    fun finishWorkoutSession(sessionFeel: Int = 4) {
        if (!isCommitting.compareAndSet(false, true)) return
        com.apexfit.app.utils.WorkoutForegroundService.stop(getApplication())
        viewModelScope.launch {
            _saveError.value = null
            val session = sessionManager.activeSession.value
            val completedSets = withContext(Dispatchers.Default) {
                val sets = mutableListOf<UiExerciseSet>()
                session?.exercises?.forEach { ex ->
                    ex.sets.filter { it.completed }.forEach { setObj ->
                        sets.add(
                            ExerciseSet(
                                exerciseId = ex.exerciseId,
                                exerciseName = ex.exerciseName,
                                muscleGroup = ex.muscleGroup,
                                weight = setObj.weight,
                                reps = setObj.reps,
                                rpe = setObj.rpe,
                                isWarmup = setObj.isWarmup,
                                restTaken = setObj.restTakenSeconds,
                                completed = setObj.completed,
                                effectiveSetValue = com.apexfit.app.utils.ProgressionEngine.calculateEffectiveSetValue(setObj.rpe)
                            ).toUi()
                        )
                    }
                }
                sets
            }

            try {
                _lastCompletedSessionSets.value = completedSets
                val result = sessionManager.commitToDatabase(sessionFeel)
                sessionManager.clearPersistedSession()
                _completedStats.value = result.durationMinutes to (result.totalVolumeTonnes * 1000.0)

                _completedPRsBroken.value = result.confirmedPRs.map { exerciseId ->
                    val exerciseName = session?.exercises?.find { it.exerciseId == exerciseId }?.exerciseName ?: "Exercise"
                    "$exerciseName PR Conquered!"
                }

                val score = withContext(Dispatchers.Default) {
                    calculateWorkoutSessionHypertrophyQualityScore(completedSets)
                }
                _completedHypertrophyScore.value = score
                
                // Enqueue PatternScanWorker asynchronously to process patterns off the main commit path
                try {
                    val scanRequest = OneTimeWorkRequestBuilder<PatternScanWorker>()
                        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
                        .build()
                    WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                        "pattern_scan_after_session",
                        ExistingWorkPolicy.REPLACE,
                        scanRequest
                    )
                } catch (we: Exception) {
                    android.util.Log.e("WorkoutExecutionViewModel", "Failed to enqueue pattern scan", we)
                }

                _showSessionCompleteScreen.value = true
                isCommitting.set(false)
            } catch (e: Exception) {
                _saveError.value = "Failed to save workout: ${e.localizedMessage}"
                isCommitting.set(false)
            }
        }
    }

    private fun calculateWorkoutSessionHypertrophyQualityScore(sets: List<UiExerciseSet>): Double {
        if (sets.isEmpty()) {
            return 0.0
        }
        var scoreSum = 0.0
        sets.forEach { set ->
            val rpeMod = com.apexfit.app.utils.ProgressionEngine.calculateEffectiveSetValue(set.rpe)
            val isCompound = set.exerciseName.lowercase().let { n ->
                n.contains("squat") || n.contains("press") || n.contains("deadlift") || n.contains("row") || n.contains("pullup") || n.contains("dips")
            }
            val typeMod = if (isCompound) 1.2 else 1.05
            scoreSum += (rpeMod * typeMod)
        }
        val pct = (scoreSum / sets.size) * 10.0
        return Math.min(10.0, Math.max(0.0, pct))
    }

    fun dismissSessionComplete() {
        _showSessionCompleteScreen.value = false
    }

    fun skipExercise(sessionFeel: Int = 4) {
        val exercisesSize = activeExercises.value.size
        if (_currentExerciseIdx.value < exercisesSize - 1) {
            _currentExerciseIdx.value += 1
        } else {
            finishWorkoutSession(sessionFeel)
        }
    }

    fun finishWorkoutEarly(sessionFeel: Int = 4) {
        finishWorkoutSession(sessionFeel)
    }

    fun savePartialAndExit(sessionFeel: Int) {
        if (!isCommitting.compareAndSet(false, true)) return
        com.apexfit.app.utils.WorkoutForegroundService.stop(getApplication())
        viewModelScope.launch {
            _saveError.value = null
            val session = sessionManager.activeSession.value
            val completedSets = withContext(Dispatchers.Default) {
                val sets = mutableListOf<UiExerciseSet>()
                session?.exercises?.forEach { ex ->
                    ex.sets.filter { it.completed }.forEach { setObj ->
                        sets.add(
                            ExerciseSet(
                                exerciseId = ex.exerciseId,
                                exerciseName = ex.exerciseName,
                                muscleGroup = ex.muscleGroup,
                                weight = setObj.weight,
                                reps = setObj.reps,
                                rpe = setObj.rpe,
                                isWarmup = setObj.isWarmup,
                                restTaken = setObj.restTakenSeconds,
                                completed = setObj.completed,
                                effectiveSetValue = com.apexfit.app.utils.ProgressionEngine.calculateEffectiveSetValue(setObj.rpe)
                            ).toUi()
                        )
                    }
                }
                sets
            }

            try {
                _lastCompletedSessionSets.value = completedSets
                val result = sessionManager.savePartialAndExit(sessionFeel)
                sessionManager.clearPersistedSession()
                _completedStats.value = result.durationMinutes to (result.totalVolumeTonnes * 1000.0)
                
                _completedPRsBroken.value = result.confirmedPRs.map { exerciseId ->
                    val exerciseName = session?.exercises?.find { it.exerciseId == exerciseId }?.exerciseName ?: "Exercise"
                    "$exerciseName PR Conquered!"
                }

                val score = withContext(Dispatchers.Default) {
                    calculateWorkoutSessionHypertrophyQualityScore(completedSets)
                }
                _completedHypertrophyScore.value = score

                _showSessionCompleteScreen.value = true
                isCommitting.set(false)
            } catch (e: Exception) {
                _saveError.value = "Failed to save partial workout: ${e.localizedMessage}"
                isCommitting.set(false)
            }
        }
    }

    val hasCompletedSets: Boolean
        get() = sessionManager.hasCompletedSets
}
