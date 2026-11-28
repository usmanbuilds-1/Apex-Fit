@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import com.apexfit.app.utils.fromDisplayWeightToKg

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
import com.apexfit.app.utils.WorkoutResumeState
import com.apexfit.app.utils.CircuitExecution
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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
    private val _isSavingSet = MutableStateFlow(false)
    val isSavingSet: StateFlow<Boolean> = _isSavingSet.asStateFlow()
    private val _setSaveError = MutableStateFlow<String?>(null)
    val setSaveError: StateFlow<String?> = _setSaveError.asStateFlow()
    fun dismissSetSaveError() { _setSaveError.value = null }

    // User weight from preferences for relative calculations
    val currentWeight = repository.getCurrentWeightFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.apexfit.app.UserDefaults.WEIGHT_KG)
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
        active?.exercises?.mapIndexed { index, entry ->
            PlanExercise(
                id = index.toLong() + 1L,
                planSessionId = active.planSessionId.toLongOrNull() ?: 0L,
                exerciseId = entry.exerciseId,
                name = entry.exerciseName,
                muscleGroup = entry.muscleGroup,
                sets = entry.sets.size,
                repsMin = entry.targetRepsMin ?: entry.sets.firstOrNull()?.reps ?: 6,
                repsMax = entry.targetRepsMax ?: entry.sets.firstOrNull()?.reps ?: 12,
                weight = entry.sets.firstOrNull()?.weight ?: 0.0,
                restSeconds = entry.restSeconds ?: 90,
                notes = ""
            )
        } ?: emptyList()
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentExerciseIdx = MutableStateFlow(0)
    val currentExerciseIdx: StateFlow<Int> = _currentExerciseIdx.asStateFlow()

    init {
        // Restore the first unfinished exercise only when entering a new session.
        // Never reset the cursor on ordinary set edits.
        viewModelScope.launch {
            sessionManager.activeSession.map { it?.sessionId }.distinctUntilChanged().collect { id ->
                val active = sessionManager.activeSession.value
                if (id != null && active?.sessionId == id) {
                    _currentExerciseIdx.value = WorkoutResumeState.cursor(active)
                }
            }
        }
    }

    val loggedSets: StateFlow<Map<String, List<UiExerciseSet>>> = sessionManager.activeSession.map { active ->
        if (active != null) {
            active.exercises.associate { exercise ->
                exercise.executionKey to exercise.sets.mapIndexed { sIdx, setObj ->
                    ExerciseSet(
                        id = sIdx.toLong(),
                        sessionId = active.sessionId,
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

                exercise.executionKey to EffectiveSetsData(
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

    private val _completedReceipt = MutableStateFlow<SessionCommitResult?>(null)
    val completedReceipt: StateFlow<SessionCommitResult?> = _completedReceipt.asStateFlow()

    data class RirOverlayState(
        val isVisible: Boolean = false,
        val exerciseId: String = "",
        val exerciseName: String = "",
        val muscleGroup: String = "",
        val setIndex: Int = 0,
        val weight: Double = 0.0,
        val reps: Int = 0,
        val totalSets: Int = 0,
        val selectedRir: Int = com.apexfit.app.utils.EffortSelection.UNRATED,
        val loadBasis: com.apexfit.app.utils.RecordedLoadBasis? = null,
        val loadSetup: String = "",
        val freeWeightRepLimit: Boolean = false,
        val hasLoggedSet: Boolean = false,
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.apexfit.app.utils.EffortSelection.UNRATED)

    val rirHistoricalSets: StateFlow<List<com.apexfit.app.data.LastSetWithDate>> = _rirOverlayState.map { it.historicalSets }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    fun dismissSaveError() {
        _saveError.value = null
    }

    private val _completedHypertrophyScore = MutableStateFlow(0.0)
    val completedHypertrophyScore: StateFlow<Double> = _completedHypertrophyScore.asStateFlow()

    init {
        var observedGeneration = sessionManager.resetGeneration.value
        viewModelScope.launch {
            sessionManager.resetGeneration.collect { generation ->
                if (generation != observedGeneration) {
                    observedGeneration = generation
                    _completedReceipt.value = null
                    _lastCompletedSessionSets.value = emptyList()
                    _completedHypertrophyScore.value = 0.0
                    _showSessionCompleteScreen.value = false
                    _rirOverlayState.value = RirOverlayState()
                    _currentExerciseIdx.value = 0
                    lastCompletedSetPointer = null
                }
            }
        }
        viewModelScope.launch {
            sessionManager.recoveredReceipt.collect { receipt ->
                if (receipt != null && sessionManager.activeSession.value == null) publishReceipt(receipt)
            }
        }
    }

    private fun publishReceipt(receipt: SessionCommitResult) {
        val sets = receipt.savedSets.filter { it.completed }.map { it.toUi() }
        _lastCompletedSessionSets.value = sets
        _completedHypertrophyScore.value = calculateWorkoutSessionHypertrophyQualityScore(sets)
        _completedReceipt.value = receipt
        _showSessionCompleteScreen.value = true
    }

    var lastCompletedSetPointer: Pair<String, Int>? = null
        private set

    fun startWorkoutSession(planSession: PlanSession) {
        if (sessionManager.activeSession.value != null || sessionManager.isStartingSession.value) return
        _currentExerciseIdx.value = 0
        _showSessionCompleteScreen.value = false
        _warmupCompleted.value = _warmupCompleted.value.mapValues { false }
        lastCompletedSetPointer = null
        val generation = sessionManager.resetGeneration.value
        // AUDIT FIX (BUG-V4-002): FGS start moved into sessionManager.startSession,
        // which calls it after the session is built and while the app is still
        // foreground. Removed premature start here to prevent orphaned FGS.
        viewModelScope.launch {
            _saveError.value = null
            try {
                val exercises = dao.getExercisesForSession(planSession.id)
                sessionManager.startSession(
                    planSession = planSession,
                    exercises = exercises,
                    userWeight = currentWeight.value,
                    userHeight = userHeight.value,
                    preferredUnits = units.value,
                    expectedResetGeneration = generation
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveError.value = "Couldn't start workout. Please try again."
            }
        }
    }

    fun cancelActiveWorkout(): Job? {
        val active = sessionManager.activeSession.value ?: return null
        if (isCommitting.get() || !_isSavingSet.compareAndSet(false, true)) return null
        return viewModelScope.launch {
            try {
                check(sessionManager.discardDraftDurably(active.sessionId))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveError.value = "Couldn't discard the workout. Your saved draft was retained."
            } finally {
                _isSavingSet.value = false
            }
        }
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

    private fun persistConfirmedSet(
        exerciseId: String, setIndex: Int, weight: Double, reps: Int, rpe: Int,
        completed: Boolean, restTakenSeconds: Int = 0, repsInReserve: Int? = null,
        advanceAfterCompletion: Boolean = false,
        advanced: com.apexfit.app.utils.AdvancedSetDetails? = null,
        observation: com.apexfit.app.utils.SetObservation? = null,
        onSaved: () -> Unit = {}
    ): Job? {
        val session = sessionManager.activeSession.value ?: return null
        val set = session.exercises.firstOrNull { it.executionKey == exerciseId }?.sets?.getOrNull(setIndex) ?: return null
        if (isCommitting.get() || !_isSavingSet.compareAndSet(false, true)) return null
        _setSaveError.value = null
        return viewModelScope.launch {
            try {
                check(sessionManager.updateSetDurably(session.sessionId, exerciseId, setIndex,
                    weight, reps, rpe, restTakenSeconds, completed, repsInReserve, advanceAfterCompletion, advanced, observation)) {
                    "Workout changed before this set could be saved."
                }
                if (sessionManager.activeSession.value?.sessionId != session.sessionId) return@launch
                if (advanceAfterCompletion) {
                    sessionManager.activeSession.value?.let { _currentExerciseIdx.value = WorkoutResumeState.cursor(it) }
                }
                if (completed) {
                    lastCompletedSetPointer = Pair(exerciseId, setIndex)
                    // Use the logged set's units, not a possibly changed preference.
                    val weightKg = weight.fromDisplayWeightToKg(set.weightUnit)
                    if (advanced == null && set.advancedDetails == null)
                        viewModelScope.launch { sessionManager.checkPRPreview(exerciseId, weightKg, reps) }
                }
                try {
                    onSaved()
                } catch (e: Exception) {
                    _setSaveError.value = "Set was saved, but the next workout action could not start."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _setSaveError.value = "Set could not be saved. Your previous saved set is unchanged. Try again."
            } finally {
                _isSavingSet.value = false
            }
        }
    }

    fun logWorkoutSetState(exerciseId: String, setIndex: Int, weight: Double, reps: Int, rpe: Int, completed: Boolean, restTakenSeconds: Int = 0, repsInReserve: Int? = null) {
        if (_isSavingSet.value || isCommitting.get()) return
        val previous = sessionManager.activeSession.value?.exercises?.firstOrNull { it.executionKey == exerciseId }
            ?.sets?.getOrNull(setIndex) ?: return
        if (completed || previous.completed) {
            // Ignore no-op focus-loss updates; do not launch another disk write.
            if (previous.weight == weight && previous.reps == reps && previous.rpe == rpe &&
                previous.completed == completed && previous.restTakenSeconds == restTakenSeconds &&
                (repsInReserve == null || previous.repsInReserve == repsInReserve)) return
            persistConfirmedSet(exerciseId, setIndex, weight, reps, rpe, completed, restTakenSeconds, repsInReserve)
        } else {
            sessionManager.updateSet(exerciseId, setIndex, weight, reps, rpe, restTakenSeconds, completed, repsInReserve)
        }
    }

    fun addCustomLogSet(exerciseId: String) {
        val active = sessionManager.activeSession.value ?: return
        if (isCommitting.get() || !_isSavingSet.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                check(sessionManager.addCustomSetDurably(active.sessionId, exerciseId))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _setSaveError.value = "Couldn't save the extra set. Try again."
            } finally {
                _isSavingSet.value = false
            }
        }
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
        if (_isSavingSet.value || isCommitting.get()) return
        _rirOverlayState.value = _rirOverlayState.value.copy(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            muscleGroup = muscleGroup,
            setIndex = setIndex,
            weight = weight,
            reps = reps,
            totalSets = totalSets,
            selectedRir = com.apexfit.app.utils.EffortSelection.UNRATED,
            loadBasis = null, loadSetup = "", freeWeightRepLimit = false,
            hasLoggedSet = false,
            isShowingHistory = false,
            historicalSets = emptyList(),
            isVisible = true
        )
    }

    fun selectLoadBasis(basis: com.apexfit.app.utils.RecordedLoadBasis?) {
        val state = _rirOverlayState.value
        if (_isSavingSet.value || state.hasLoggedSet || !state.isVisible) return
        _rirOverlayState.value = state.copy(loadBasis = basis, loadSetup = if (basis == null) "" else state.loadSetup,
            freeWeightRepLimit = false)
    }

    fun setLoadSetup(setup: String) {
        val state = _rirOverlayState.value
        if (_isSavingSet.value || state.hasLoggedSet || !state.isVisible) return
        _rirOverlayState.value = state.copy(loadSetup = setup.take(48), freeWeightRepLimit = false)
    }

    fun setFreeWeightRepLimit(confirmed: Boolean) {
        val state = _rirOverlayState.value
        if (_isSavingSet.value || state.hasLoggedSet || !state.isVisible) return
        _rirOverlayState.value = state.copy(freeWeightRepLimit = confirmed && state.selectedRir == 0 &&
            state.loadBasis == com.apexfit.app.utils.RecordedLoadBasis.TOTAL && state.reps in 2..5)
    }

    private fun observationFor(state: RirOverlayState): com.apexfit.app.utils.SetObservation? {
        val set = sessionManager.activeSession.value?.exercises?.firstOrNull { it.executionKey == state.exerciseId }
            ?.sets?.getOrNull(state.setIndex) ?: return null
        if (set.isWarmup) return null
        return com.apexfit.app.utils.SetObservation(state.weight.fromDisplayWeightToKg(set.weightUnit), state.reps,
            com.apexfit.app.utils.EffortSelection.actualRir(state.selectedRir), state.loadBasis,
            if (state.loadBasis == null) "" else state.loadSetup.trim(), state.freeWeightRepLimit)
            .takeIf(com.apexfit.app.utils.SetObservationCodec::valid)
    }

    fun selectRirOption(rir: Int, onSaved: (() -> Unit)? = null): Job? {
        if (_isSavingSet.value || isCommitting.get()) return null
        val current = _rirOverlayState.value
        if (!com.apexfit.app.utils.EffortSelection.isRated(rir)) return null
        val next = current.copy(selectedRir = rir, freeWeightRepLimit = current.freeWeightRepLimit && rir == 0)
        if (!current.hasLoggedSet) {
            _rirOverlayState.value = next
            onSaved?.invoke()
            return null
        }
        val recordedRestSeconds = sessionManager.activeSession.value?.exercises
            ?.firstOrNull { it.executionKey == current.exerciseId }
            ?.sets?.getOrNull(current.setIndex)?.restTakenSeconds ?: return null
        return persistConfirmedSet(current.exerciseId, current.setIndex, current.weight, current.reps,
            com.apexfit.app.utils.ProgressionEngine.calculateRPEFromRIR(next.selectedRir),
            completed = true, restTakenSeconds = recordedRestSeconds, repsInReserve = next.selectedRir,
            observation = observationFor(next)) {
            _rirOverlayState.value = _rirOverlayState.value.copy(selectedRir = next.selectedRir,
                freeWeightRepLimit = next.freeWeightRepLimit)
            onSaved?.invoke()
        }
    }

    fun confirmRirSelection() {
        val state = _rirOverlayState.value
        val exerciseId = sessionManager.activeSession.value?.exercises?.firstOrNull { it.executionKey == state.exerciseId }?.exerciseId ?: return
        if (!com.apexfit.app.utils.EffortSelection.isRated(state.selectedRir)) return
        val rpe = com.apexfit.app.utils.EffortSelection.rpe(state.selectedRir)

        viewModelScope.launch {
            val lastSets = dao.getExerciseSetsByExerciseIdAndRPE(exerciseId, rpe, 5)
            if (_rirOverlayState.value.exerciseId != state.exerciseId || _rirOverlayState.value.setIndex != state.setIndex || !_rirOverlayState.value.isVisible) return@launch
            _rirOverlayState.value = _rirOverlayState.value.copy(
                historicalSets = lastSets,
                isShowingHistory = true
            )
        }
    }

    fun returnToRirSelection() {
        _rirOverlayState.value = _rirOverlayState.value.copy(isShowingHistory = false)
    }

    fun confirmRirAndNextSet(
        onTriggerRestTimer: ((rpe: Int, exerciseName: String, muscleGroup: String, weight: Double, reps: Int, setIndex: Int, totalSets: Int) -> Unit)? = null
    ): Job? {
        val s = _rirOverlayState.value
        if (s.hasLoggedSet || !s.isVisible) return null
        val exId = s.exerciseId
        val exName = s.exerciseName
        val muscleGroup = s.muscleGroup
        val sIdx = s.setIndex
        val w = s.weight
        val r = s.reps
        val totalSets = s.totalSets
        val repsInReserve = com.apexfit.app.utils.EffortSelection.actualRir(s.selectedRir)
        val calculatedRpe = com.apexfit.app.utils.EffortSelection.rpe(s.selectedRir)

        val observation = observationFor(s)
        if (s.loadBasis != null && observation == null) {
            _setSaveError.value = "Enter a valid setup name or select unknown load."
            return null
        }
        return persistConfirmedSet(exId, sIdx, w, r, calculatedRpe, completed = true,
            restTakenSeconds = 0, repsInReserve = repsInReserve, advanceAfterCompletion = true,
            observation = observation) {
            _rirOverlayState.value = _rirOverlayState.value.copy(hasLoggedSet = true)
            if (sessionManager.activeSession.value?.restCheckpoint != null) {
                onTriggerRestTimer?.invoke(calculatedRpe, exName, muscleGroup, w, r, sIdx, totalSets)
            }
        }
    }

    fun completeSetWithRpe(
        exerciseId: String, exerciseName: String, muscleGroup: String,
        setIndex: Int, weight: Double, reps: Int, totalSets: Int, rpe: Int,
        onRest: () -> Unit
    ): Job? = persistConfirmedSet(exerciseId, setIndex, weight, reps, rpe, completed = true,
        repsInReserve = (10 - rpe).coerceIn(0, 9), advanceAfterCompletion = true) {
        if (sessionManager.activeSession.value?.restCheckpoint != null) onRest()
    }

    fun closeRirSelector() {
        if (_isSavingSet.value) return
        _rirOverlayState.value = _rirOverlayState.value.copy(isVisible = false)
    }

    private fun moveExercise(skipped: Boolean, sessionFeel: Int = 4, onSaved: (() -> Unit)? = null) {
        if (isCommitting.get() || !_isSavingSet.compareAndSet(false, true)) return
        val active = sessionManager.activeSession.value
        if (active == null || active.exercises.isEmpty()) {
            _isSavingSet.value = false
            return
        }
        val index = WorkoutResumeState.cursor(active)
        val grouped = CircuitExecution.contains(active, index)
        var finalExercise = index == active.exercises.lastIndex
        viewModelScope.launch {
            var moved = false
            try {
                check(sessionManager.advanceCursorDurably(active.sessionId, index, skipped)) {
                    "Workout changed or rest is still open."
                }
                if (sessionManager.activeSession.value?.sessionId != active.sessionId) return@launch
                sessionManager.activeSession.value?.let {
                    _currentExerciseIdx.value = WorkoutResumeState.cursor(it)
                    if (grouped) finalExercise = CircuitExecution.terminal(it, index)
                }
                moved = true
                onSaved?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _setSaveError.value = "Couldn't save workout position. Close the rest timer and try again."
            } finally {
                _isSavingSet.value = false
            }
            if (moved && finalExercise) {
                if (skipped) savePartialAndExit(sessionFeel) else finishWorkoutSession(sessionFeel)
            }
        }
    }

    fun logAdvancedBout(exerciseId: String, setIndex: Int, weight: Double, reps: Int,
                        details: com.apexfit.app.utils.AdvancedSetDetails): Job? =
        persistConfirmedSet(exerciseId, setIndex, weight, reps, 0, completed = true,
            advanceAfterCompletion = true, advanced = details)

    fun applyProgression(exerciseKey: String, proposal: com.apexfit.app.utils.ConfirmedProgressionProposal) {
        val active = sessionManager.activeSession.value ?: return
        if (isCommitting.get() || !_isSavingSet.compareAndSet(false, true)) return
        _setSaveError.value = null
        viewModelScope.launch {
            try { check(sessionManager.applyProgressionDurably(active.sessionId, exerciseKey, proposal)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _setSaveError.value = "Suggestion is no longer valid. Use the load fields to adjust manually." }
            finally { _isSavingSet.value = false }
        }
    }

    fun configureCircuit(group: ActiveCircuit?) {
        val active = sessionManager.activeSession.value ?: return
        if (isCommitting.get() || !_isSavingSet.compareAndSet(false, true)) return
        _setSaveError.value = null
        viewModelScope.launch {
            try {
                check(sessionManager.configureCircuitDurably(active.sessionId, group))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _setSaveError.value = "Could not configure circuit. Configure before logging any sets." }
            finally { _isSavingSet.value = false }
        }
    }

    fun nextExercise() = moveExercise(skipped = false)

    fun finishWorkoutSession(sessionFeel: Int = 4) {
        if (_isSavingSet.value || !isCommitting.compareAndSet(false, true)) return
        val generation = sessionManager.resetGeneration.value
        viewModelScope.launch {
            _saveError.value = null
            try {
                val result = sessionManager.commitToDatabase(sessionFeel)
                if (sessionManager.resetGeneration.value != generation) return@launch
                check(result.sessionId.isNotBlank()) { "No active workout to save." }
                publishReceipt(result)
                
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

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (sessionManager.resetGeneration.value == generation)
                    _saveError.value = "Failed to save workout: ${e.localizedMessage}"
            } finally {
                isCommitting.set(false)
            }
        }
    }

    private fun calculateWorkoutSessionHypertrophyQualityScore(sets: List<UiExerciseSet>): Double {
        val workingSets = sets.filter { it.completed && !it.isWarmup }
        if (workingSets.isEmpty()) return 0.0

        // This is a session effort/proximity score, not a prediction of hypertrophy.
        // Keep it aligned with the app's effective-set RPE mapping and avoid arbitrary
        // exercise-name bonuses that made "compound" strings score higher by default.
        val averageEffectiveValue = workingSets
            .map { com.apexfit.app.utils.ProgressionEngine.calculateEffectiveSetValue(it.rpe) }
            .average()
        return (averageEffectiveValue * 10.0).coerceIn(0.0, 10.0)
    }

    fun dismissSessionComplete() {
        val receipt = _completedReceipt.value
        if (receipt == null) {
            _showSessionCompleteScreen.value = false
            return
        }
        viewModelScope.launch {
            try {
                sessionManager.dismissReceipt(receipt.sessionId)
                // A delayed dismissal must not hide a newer workout receipt.
                if (_completedReceipt.value?.sessionId == receipt.sessionId) {
                    _showSessionCompleteScreen.value = false
                    _completedReceipt.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_completedReceipt.value?.sessionId == receipt.sessionId) {
                    _saveError.value = "Workout saved; could not dismiss receipt. Try again."
                }
                android.util.Log.e("WorkoutExecutionViewModel", "Receipt dismissal failed", e)
            }
        }
    }

    fun skipExercise(sessionFeel: Int = 4, onSaved: (() -> Unit)? = null) =
        moveExercise(skipped = true, sessionFeel = sessionFeel, onSaved = onSaved)

    fun finishWorkoutEarly(sessionFeel: Int = 4) {
        savePartialAndExit(sessionFeel)
    }

    fun savePartialAndExit(sessionFeel: Int) {
        if (_isSavingSet.value || !isCommitting.compareAndSet(false, true)) return
        val generation = sessionManager.resetGeneration.value
        viewModelScope.launch {
            _saveError.value = null
            try {
                val result = sessionManager.savePartialAndExit(sessionFeel)
                if (sessionManager.resetGeneration.value != generation) return@launch
                check(result.sessionId.isNotBlank()) { "No active workout to save." }
                publishReceipt(result)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (sessionManager.resetGeneration.value == generation)
                    _saveError.value = "Failed to save partial workout: ${e.localizedMessage}"
            } finally {
                isCommitting.set(false)
            }
        }
    }

    val hasCompletedSets: Boolean
        get() = sessionManager.hasCompletedSets
}
