@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.repository.FitnessRepository
import com.example.data.repository.FitnessRepositoryImpl
import com.example.ui.models.*
import com.example.utils.AudioService
import com.example.utils.ProgressionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.room.withTransaction
import android.content.Intent
import androidx.core.content.ContextCompat

class TrainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = com.example.di.ServiceLocator.database(application)
    private val dao = db.fitnessDao()
    private val dataStore = com.example.di.ServiceLocator.dataStore(application)
    private val repository = com.example.di.ServiceLocator.repository(application)
    val sessionManager = WorkoutSessionManager(repository, dataStore, viewModelScope)

    init {
        viewModelScope.launch {
            try {
                val existing = dao.getAllPlans()
                if (existing.isEmpty()) {
                    seedDefaultWorkoutPlan()
                }
            } catch (e: Exception) {
                android.util.Log.e("TrainViewModel", "Failed to seed default plan: ${e.message}", e)
            }
        }
    }

    // User weight from preferences for relative calculations
    val currentWeight = dataStore.currentWeightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.UserDefaults.WEIGHT_KG)
    val userHeight = dataStore.heightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.UserDefaults.HEIGHT_CM)
    val units = dataStore.unitsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "kg")

    // Training State (Plans, Sessions)
    val workoutPlans = repository.getWorkoutPlans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val baseActivePlan = repository.getActivePlan()
    private val baseActivePlanSessions = baseActivePlan.flatMapLatest { plan ->
        if (plan != null) dao.getSessionsForPlanFlow(plan.id) else flowOf(emptyList())
    }

    val activePlan: StateFlow<UiState<WorkoutPlan?>> = baseActivePlan
        .map { UiState.Success(it) as UiState<WorkoutPlan?> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val activePlanSessions: StateFlow<UiState<List<PlanSession>>> = baseActivePlanSessions
        .map { UiState.Success(it) as UiState<List<PlanSession>> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    // Plan Builder Editing State
    val planBuilderSessions = MutableStateFlow<List<PlanSession>>(emptyList())
    val planBuilderExercises = MutableStateFlow<List<PlanExercise>>(emptyList())
    var isInitialized = false

    fun initializePlanBuilder(plan: WorkoutPlan?, sessions: List<PlanSession>, exercises: List<PlanExercise>) {
        if (!isInitialized) {
            planBuilderSessions.value = sessions
            planBuilderExercises.value = exercises
            isInitialized = true
        }
    }

    fun addSession(session: PlanSession) {
        planBuilderSessions.value = planBuilderSessions.value + session
    }

    fun updateSession(session: PlanSession) {
        planBuilderSessions.value = planBuilderSessions.value.map { if (it.id == session.id) session else it }
    }

    fun deleteSession(sessionId: Long) {
        planBuilderSessions.value = planBuilderSessions.value.filter { it.id != sessionId }
        planBuilderExercises.value = planBuilderExercises.value.filter { it.planSessionId != sessionId }
    }

    fun addExercise(exercise: PlanExercise) {
        planBuilderExercises.value = planBuilderExercises.value + exercise
    }

    fun addCustomExercise(planExercise: PlanExercise) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val slug = planExercise.name.lowercase()
                    .replace(Regex("[^a-z0-9\\s-]"), "")
                    .replace(Regex("\\s+"), "-")
                    .trim()
                val existing = dao.getExerciseById(slug)
                if (existing == null) {
                    dao.insertExercises(listOf(
                        Exercise(
                            id = slug,
                            name = planExercise.name,
                            category = "User Created",
                            primaryMuscle = planExercise.muscleGroup,
                            secondaryMuscles = emptyList(),
                            equipmentRequired = "Unknown",
                            isBilateral = 1,
                            isUserCreated = 1,
                            isDeleted = 0,
                            createdAt = System.currentTimeMillis()
                        )
                    ))
                    dao.insertExerciseMetadata(ExerciseMetadata(exerciseId = slug))
                }
                addExercise(planExercise)
            }
        }
    }

    fun updateExercise(exercise: PlanExercise) {
        planBuilderExercises.value = planBuilderExercises.value.map { if (it.id == exercise.id) exercise else it }
    }

    fun deleteExercise(exerciseId: Long) {
        planBuilderExercises.value = planBuilderExercises.value.filter { it.id != exerciseId }
    }

    fun getSessionById(id: Long): PlanSession? {
        return planBuilderSessions.value.find { it.id == id }
    }

    fun getExerciseById(id: Long): PlanExercise? {
        return planBuilderExercises.value.find { it.id == id }
    }

    fun reorderSession(index: Int, moveUp: Boolean) {
        val list = planBuilderSessions.value.toMutableList()
        val swapIndex = if (moveUp) index - 1 else index + 1
        if (swapIndex in list.indices) {
            val temp = list[index]
            list[index] = list[swapIndex]
            list[swapIndex] = temp
            planBuilderSessions.value = list
        }
    }

    private val _selectedDayOfWeek = MutableStateFlow("Monday")
    val selectedDayOfWeek: StateFlow<String> = _selectedDayOfWeek.asStateFlow()

    val allPlanExercises = dao.getAllPlanExercisesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedDaySession = combine(baseActivePlanSessions, _selectedDayOfWeek) { sessions, day ->
        sessions.firstOrNull { it.day.equals(day, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedDayExercises = selectedDaySession.flatMapLatest { session ->
        if (session != null) repository.getExercisesForSession(session.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayExercises: StateFlow<UiState<List<PlanExercise>>> = baseActivePlanSessions.flatMapLatest { sessions ->
        val todayDayString = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
        val todaySession = sessions.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
        if (todaySession != null) repository.getExercisesForSession(todaySession.id) else flowOf(emptyList())
    }
    .map { UiState.Success(it) as UiState<List<PlanExercise>> }
    .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun selectDay(day: String) {
        _selectedDayOfWeek.value = day
        _activeSubstIndex.value = -1
        _substitutionList.value = emptyList()
    }

    // Hoisted TrainScreen UI state
    private val _activeSubstIndex = MutableStateFlow<Int?>(-1)
    val activeSubstIndex: StateFlow<Int?> = _activeSubstIndex.asStateFlow()

    private val _substitutionList = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val substitutionList: StateFlow<List<Pair<String, String>>> = _substitutionList.asStateFlow()

    fun selectSubstIndex(index: Int?) {
        _activeSubstIndex.value = index
    }

    fun setSubstitutionList(list: List<Pair<String, String>>) {
        _substitutionList.value = list
    }

    fun loadSubstitutionSuggestions(muscleGroup: String, exerciseName: String) {
        viewModelScope.launch {
            _substitutionList.value = getSubstitutionSuggestions(muscleGroup, exerciseName)
        }
    }

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeExercises: StateFlow<List<PlanExercise>> = sessionManager.activeSession.map { active ->
        active?.exercises?.map { entry ->
            PlanExercise(
                id = entry.exerciseId.toLongOrNull() ?: 0L,
                planSessionId = active.planSessionId.toLongOrNull() ?: 0L,
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentExerciseIdx = MutableStateFlow(0)
    val currentExerciseIdx: StateFlow<Int> = _currentExerciseIdx.asStateFlow()

    val loggedSets: StateFlow<Map<Long, List<UiExerciseSet>>> = sessionManager.activeSession.map { active ->
        if (active != null) {
            active.exercises.associate { exercise ->
                val exIdLong = exercise.exerciseId.toLongOrNull() ?: 0L
                exIdLong to exercise.sets.mapIndexed { sIdx, setObj ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val weightContextLines: StateFlow<Map<String, String>> = sessionManager.weightContextLines

    val effectiveSetsStateFlow: StateFlow<Map<String, EffectiveSetsData>> = combine(
        sessionManager.activeSession,
        activeExercises
    ) { activeSession, exercises ->
        if (activeSession == null) {
            emptyMap()
        } else {
            activeSession.exercises.associate { exercise ->
                val completedSets = exercise.sets.filter { it.completed && !it.isWarmup }
                val currentEff = completedSets.sumOf { setObj ->
                    com.example.utils.ProgressionEngine.calculateEffectiveSetValue(setObj.rpe)
                }
                
                val planEx = exercises.find { it.id.toString() == exercise.exerciseId || it.name.equals(exercise.exerciseName, ignoreCase = true) }
                val planSets = planEx?.sets ?: exercise.sets.size
                val targetEff = planSets * 0.75
                
                val lastCompletedSet = completedSets.lastOrNull()
                val lastSetEff = if (lastCompletedSet != null) {
                    com.example.utils.ProgressionEngine.calculateEffectiveSetValue(lastCompletedSet.rpe)
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
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

    private var restTimerJob: kotlinx.coroutines.Job? = null

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

    // Workout completed page state inside UI
    private val _showSessionCompleteScreen = MutableStateFlow(false)
    val showSessionCompleteScreen: StateFlow<Boolean> = _showSessionCompleteScreen.asStateFlow()

    private val _completedStats = MutableStateFlow<Pair<Int, Double>>(0 to 0.0) // Pair of (DurationMinutes, TotalVolumeWeight)
    val completedStats: StateFlow<Pair<Int, Double>> = _completedStats.asStateFlow()

    private val _completedPRsBroken = MutableStateFlow<List<String>>(emptyList())
    val completedPRsBroken: StateFlow<List<String>> = _completedPRsBroken.asStateFlow()

    // RIR Selector Overlay State
    private val _showRirOverlay = MutableStateFlow(false)
    val showRirOverlay: StateFlow<Boolean> = _showRirOverlay.asStateFlow()

    private val _isShowingRirHistory = MutableStateFlow(false)
    val isShowingRirHistory: StateFlow<Boolean> = _isShowingRirHistory.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    fun dismissSaveError() {
        _saveError.value = null
    }

    private val _rirSelectorExerciseId = MutableStateFlow(0L)
    val rirSelectorExerciseId: StateFlow<Long> = _rirSelectorExerciseId.asStateFlow()

    private val _rirSelectorExerciseName = MutableStateFlow("")
    val rirSelectorExerciseName: StateFlow<String> = _rirSelectorExerciseName.asStateFlow()

    private val _rirSelectorMuscleGroup = MutableStateFlow("")
    val rirSelectorMuscleGroup: StateFlow<String> = _rirSelectorMuscleGroup.asStateFlow()

    private val _rirSelectorSetIndex = MutableStateFlow(0)
    val rirSelectorSetIndex: StateFlow<Int> = _rirSelectorSetIndex.asStateFlow()

    private val _rirSelectorWeight = MutableStateFlow(0.0)
    val rirSelectorWeight: StateFlow<Double> = _rirSelectorWeight.asStateFlow()

    private val _rirSelectorReps = MutableStateFlow(0)
    val rirSelectorReps: StateFlow<Int> = _rirSelectorReps.asStateFlow()

    private val _rirSelectorTotalSets = MutableStateFlow(0)
    val rirSelectorTotalSets: StateFlow<Int> = _rirSelectorTotalSets.asStateFlow()

    private val _selectedRir = MutableStateFlow(2) // Default = 2 (suggested)
    val selectedRir: StateFlow<Int> = _selectedRir.asStateFlow()

    private val _rirHistoricalSets = MutableStateFlow<List<com.example.data.LastSetWithDate>>(emptyList())
    val rirHistoricalSets: StateFlow<List<com.example.data.LastSetWithDate>> = _rirHistoricalSets.asStateFlow()

    private val _completedHypertrophyScore = MutableStateFlow(0.0)
    val completedHypertrophyScore: StateFlow<Double> = _completedHypertrophyScore.asStateFlow()

    private var lastCompletedSetPointer: Pair<Long, Int>? = null

    fun startWorkoutSession(planSession: PlanSession) {
        _currentExerciseIdx.value = 0
        _showSessionCompleteScreen.value = false
        _warmupCompleted.value = _warmupCompleted.value.mapValues { false }
        lastCompletedSetPointer = null

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

    fun logWorkoutSetState(exerciseId: Long, setIndex: Int, weight: Double, reps: Int, rpe: Int, completed: Boolean, restTakenSeconds: Int = 0, repsInReserve: Int? = null) {
        if (completed) {
            lastCompletedSetPointer = Pair(exerciseId, setIndex)
            viewModelScope.launch {
                sessionManager.checkPRPreview(exerciseId.toString(), weight, reps)
            }
        }
        sessionManager.updateSet(
            exerciseId = exerciseId.toString(),
            setIndex = setIndex,
            weight = weight,
            reps = reps,
            rpe = rpe,
            restTakenSeconds = restTakenSeconds,
            completed = completed,
            repsInReserve = repsInReserve
        )
    }

    private fun updateSetRestTaken(exerciseId: Long, setIndex: Int, restSeconds: Int) {
        val session = sessionManager.activeSession.value ?: return
        val exercise = session.exercises.find { it.exerciseId == exerciseId.toString() } ?: return
        if (setIndex < exercise.sets.size) {
            sessionManager.updateSet(
                exerciseId = exerciseId.toString(),
                setIndex = setIndex,
                weight = exercise.sets[setIndex].weight,
                reps = exercise.sets[setIndex].reps,
                rpe = exercise.sets[setIndex].rpe,
                restTakenSeconds = restSeconds,
                completed = exercise.sets[setIndex].completed
            )
        }
    }

    fun addCustomLogSet(exerciseId: Long) {
        sessionManager.addCustomSet(exerciseId.toString())
    }

    fun openRirSelector(
        exerciseId: Long,
        exerciseName: String,
        muscleGroup: String,
        setIndex: Int,
        weight: Double,
        reps: Int,
        totalSets: Int
    ) {
        _rirSelectorExerciseId.value = exerciseId
        _rirSelectorExerciseName.value = exerciseName
        _rirSelectorMuscleGroup.value = muscleGroup
        _rirSelectorSetIndex.value = setIndex
        _rirSelectorWeight.value = weight
        _rirSelectorReps.value = reps
        _rirSelectorTotalSets.value = totalSets
        _isShowingRirHistory.value = false

        viewModelScope.launch {
            val lastSetForEx = dao.getLastSetForExercise(exerciseId.toString())
            _selectedRir.value = lastSetForEx?.repsInReserve ?: 2
            _showRirOverlay.value = true
        }
    }

    fun selectRirOption(rir: Int) {
        _selectedRir.value = rir
    }

    fun confirmRirSelection() {
        val exerciseId = _rirSelectorExerciseId.value
        val rpe = com.example.utils.ProgressionEngine.calculateRPEFromRIR(_selectedRir.value)

        viewModelScope.launch {
            val lastSets = dao.getExerciseSetsByExerciseIdAndRPE(exerciseId.toString(), rpe, 5)
            _rirHistoricalSets.value = lastSets
            _isShowingRirHistory.value = true
        }
    }

    fun confirmRirAndNextSet() {
        val exId = _rirSelectorExerciseId.value
        val exName = _rirSelectorExerciseName.value
        val muscleGroup = _rirSelectorMuscleGroup.value
        val sIdx = _rirSelectorSetIndex.value
        val w = _rirSelectorWeight.value
        val r = _rirSelectorReps.value
        val totalSets = _rirSelectorTotalSets.value
        val repsInReserve = _selectedRir.value
        val calculatedRpe = com.example.utils.ProgressionEngine.calculateRPEFromRIR(repsInReserve)

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

        _showRirOverlay.value = false
        triggerRestTimer(
            rpe = calculatedRpe,
            exerciseName = exName,
            muscleGroup = muscleGroup,
            reps = r,
            setIndex = sIdx,
            totalSets = totalSets
        )
    }

    fun closeRirSelector() {
        _showRirOverlay.value = false
    }

    fun triggerRestTimer(rpe: Int, exerciseName: String, muscleGroup: String, reps: Int, setIndex: Int, totalSets: Int) {
        val exerciseType = com.example.utils.ProgressionEngine.getExerciseType(exerciseName, muscleGroup)
        val restSeconds = com.example.utils.ProgressionEngine.calculateRestTimeSeconds(exerciseType, rpe)

        _restTimerLastSetContext.value = "Last set: $reps reps @ RPE $rpe"

        val nextPreview = if (setIndex + 1 < totalSets) {
            "$exerciseName (set ${setIndex + 2}/$totalSets)"
        } else {
            val nextIdx = _currentExerciseIdx.value + 1
            if (nextIdx < activeExercises.value.size) {
                "${activeExercises.value[nextIdx].name} (set 1/${activeExercises.value[nextIdx].sets})"
            } else {
                "Complete Workout"
            }
        }
        _restTimerNextSetPreview.value = "Next: $nextPreview"

        _restTimerTotal.value = restSeconds
        _restTimerSeconds.value = restSeconds
        _showRestOverlay.value = true
        _isRestTimerActive.value = true

        // Schedule a one-time alarm for when the timer completes (background-safe)
        val triggerAtMillis = System.currentTimeMillis() + (restSeconds * 1000L)
        com.example.utils.RestTimerAlarmReceiver.scheduleAlarm(
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

                val elapsed = _restTimerTotal.value - remaining
                lastCompletedSetPointer?.let { (exId, sIdx) ->
                    updateSetRestTaken(exId, sIdx, elapsed)
                }

                if (remaining in 1..3) {
                    AudioService.playBeep()
                } else if (remaining == 0 && prev > 0) {
                    AudioService.playRestTimerComplete(getApplication())
                }
            }

            if (_isRestTimerActive.value) {
                // Timer completed naturally — cancel the alarm first so it doesn't fire
                // a duplicate notification after the in-app completion already played
                com.example.utils.RestTimerAlarmReceiver.cancelAlarm(getApplication())
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
        com.example.utils.RestTimerAlarmReceiver.scheduleAlarm(
            context = getApplication(),
            triggerAtMillis = triggerAtMillis,
            exerciseName = "Rest"
        )

        // Restart local timer from the new duration
        startLocalTimer(newSeconds, "Rest")
    }

    fun closeRestTimer() {
        _isRestTimerActive.value = false
        _showRestOverlay.value = false
        restTimerJob?.cancel()
        // Cancel the pending alarm — user dismissed the timer manually
        com.example.utils.RestTimerAlarmReceiver.cancelAlarm(getApplication())
    }

    fun nextExercise() {
        if (_currentExerciseIdx.value < activeExercises.value.size - 1) {
            _currentExerciseIdx.value += 1
        } else {
            finishWorkoutSession()
        }
    }

    fun finishWorkoutSession(sessionFeel: Int = 4) {
        viewModelScope.launch {
            _saveError.value = null
            val session = sessionManager.activeSession.value
            val completedSets = mutableListOf<UiExerciseSet>()
            session?.exercises?.forEach { ex ->
                ex.sets.filter { it.completed }.forEach { setObj ->
                    completedSets.add(
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
                            effectiveSetValue = com.example.utils.ProgressionEngine.calculateEffectiveSetValue(setObj.rpe)
                        ).toUi()
                    )
                }
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

                generateWorkoutSessionHypertrophyQualityScore(completedSets)
                _showSessionCompleteScreen.value = true
            } catch (e: Exception) {
                _saveError.value = "Failed to save workout: ${e.localizedMessage}"
            }
        }
    }

    private fun generateWorkoutSessionHypertrophyQualityScore(sets: List<UiExerciseSet>) {
        if (sets.isEmpty()) {
            _completedHypertrophyScore.value = 0.0
            return
        }
        var scoreSum = 0.0
        sets.forEach { set ->
            val rpeMod = when (set.rpe) {
                10 -> 1.0
                9 -> 0.95
                8 -> 0.85
                7 -> 0.70
                6 -> 0.45
                else -> 0.20
            }
            val isCompound = set.exerciseName.lowercase().let { n ->
                n.contains("squat") || n.contains("press") || n.contains("deadlift") || n.contains("row") || n.contains("pullup") || n.contains("dips")
            }
            val typeMod = if (isCompound) 1.2 else 1.05
            scoreSum += (rpeMod * typeMod)
        }
        val pct = (scoreSum / sets.size) * 10.0
        _completedHypertrophyScore.value = Math.min(10.0, Math.max(2.1, pct))
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
        viewModelScope.launch {
            _saveError.value = null
            val session = sessionManager.activeSession.value
            val completedSets = mutableListOf<UiExerciseSet>()
            session?.exercises?.forEach { ex ->
                ex.sets.filter { it.completed }.forEach { setObj ->
                    completedSets.add(
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
                            effectiveSetValue = com.example.utils.ProgressionEngine.calculateEffectiveSetValue(setObj.rpe)
                        ).toUi()
                    )
                }
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

                generateWorkoutSessionHypertrophyQualityScore(completedSets)
                _showSessionCompleteScreen.value = true
            } catch (e: Exception) {
                _saveError.value = "Failed to save partial workout: ${e.localizedMessage}"
            }
        }
    }

    val hasCompletedSets: Boolean
        get() = sessionManager.hasCompletedSets

    suspend fun getSubstitutionSuggestions(pattern: String, currentEx: String): List<Pair<String, String>> {
        return when (pattern.lowercase()) {
            "chest", "push" -> listOf(
                "Incline Dumbbell Press" to "Excellent standard pushing substitute. Shifts the focus slightly to upper pectoralis, reducing anterior deltoid shear.",
                "Weighted Chest Dips" to "Highly mechanical chest builder. Targets the lower sternal head of chest fibers."
            )
            "back", "pull" -> listOf(
                "Chest-Supported Dumbbell Row" to "Unloads the lumbar spine entirely. Allows safe high-intensity latissimus Row.",
                "Seated Cable Row" to "Provides constant tension through the entire concentric-concentric execution path."
            )
            "quads", "squat" -> listOf(
                "Hack Squat machine" to "Stabilizes the axial column. Guarantees safety when pushing close to failure.",
                "Bulgarian Split Squat" to "Unilateral powerhouse. Rectifies imbalance while loading quads/glutes."
            )
            else -> listOf(
                "Cable Lateral Raise" to "Provides superior constant resistance profile over side dumbbells.",
                "Incline Dumbbell Bicep Curl" to "Forces biceps into extreme stretch, magnifying tension and hypertrophy."
            )
        }
    }

    fun saveImportedWorkoutPlan(
        plan: WorkoutPlan,
        sessions: List<PlanSession>,
        exercises: List<PlanExercise>
    ) {
        viewModelScope.launch {
            repository.updateWorkoutPlan(plan, sessions, exercises)
        }
    }

    fun updateWorkoutPlan(
        plan: WorkoutPlan,
        sessions: List<PlanSession>,
        exercises: List<PlanExercise>
    ) {
        viewModelScope.launch {
            repository.updateWorkoutPlan(plan, sessions, exercises)
        }
    }

    fun activatePlan(planId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.withTransaction {
                    dao.deactivateAllPlans()
                    val plan = dao.getAllPlans().firstOrNull { it.id == planId } ?: return@withTransaction
                    dao.insertWorkoutPlan(plan.copy(isActive = true))
                }
            }
        }
    }

    suspend fun seedDefaultWorkoutPlan() {
        val plan = WorkoutPlan(
            id = 0,
            name = "Scientific Hypertrophy Split",
            goal = "Gain Muscle",
            isActive = true,
            createdAt = System.currentTimeMillis()
        )

        dao.deleteAllPlans()  // cascades to plan_sessions and plan_exercises via FK
        val generatedPlanId = dao.insertWorkoutPlan(plan)
        
        val sessions = listOf(
            PlanSession(id = 0, planId = generatedPlanId, label = "Upper A", day = "Monday", focus = "Chest, Back, Arms"),
            PlanSession(id = 0, planId = generatedPlanId, label = "Lower A", day = "Tuesday", focus = "Quads, Hamstrings, Calves"),
            PlanSession(id = 0, planId = generatedPlanId, label = "Upper B", day = "Thursday", focus = "Shoulders, Chest, Back"),
            PlanSession(id = 0, planId = generatedPlanId, label = "Lower B", day = "Friday", focus = "Hamstrings, Glutes, Quads")
        )

        val sessionIds = dao.insertPlanSessions(sessions)
        val upperASessionId = sessionIds[0]
        val lowerASessionId = sessionIds[1]
        val upperBSessionId = sessionIds[2]
        val lowerBSessionId = sessionIds[3]

        val exercises = listOf(
            // Monday - Upper A
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Incline Barbell Bench Press", muscleGroup = "Chest", sets = 4, repsMin = 6, repsMax = 10, weight = 80.0, restSeconds = 120, notes = "Focus on the deep stretch at chest level."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Weighted Pull-Up", muscleGroup = "Back", sets = 4, repsMin = 6, repsMax = 10, weight = 5.0, restSeconds = 120, notes = "Control the eccentric descent."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Dumbbell Lateral Raise", muscleGroup = "Shoulders", sets = 3, repsMin = 10, repsMax = 15, weight = 12.5, restSeconds = 90, notes = "Slight torso lean forward."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Incline Dumbbell Bicep Curl", muscleGroup = "Biceps", sets = 3, repsMin = 8, repsMax = 12, weight = 14.0, restSeconds = 90, notes = "Biceps fully stretched at bottom."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Dual Rope Tricep Pushdown", muscleGroup = "Triceps", sets = 3, repsMin = 10, repsMax = 15, weight = 25.0, restSeconds = 90, notes = "Flare ropes outward at end of range."),

            // Tuesday - Lower A
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Barbell Back Squat", muscleGroup = "Quads", sets = 4, repsMin = 6, repsMax = 8, weight = 100.0, restSeconds = 180, notes = "Keep knees tracking over toes."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Romanian Deadlift", muscleGroup = "Hamstrings", sets = 4, repsMin = 8, repsMax = 12, weight = 90.0, restSeconds = 120, notes = "Hinge at hips, keep back flat."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Leg Press (High & Wide)", muscleGroup = "Quads", sets = 3, repsMin = 10, repsMax = 12, weight = 160.0, restSeconds = 120, notes = "Aesthetic emphasis on quad sweep."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Seated Leg Curl", muscleGroup = "Hamstrings", sets = 3, repsMin = 10, repsMax = 15, weight = 50.0, restSeconds = 90, notes = "Hard squeeze at full flexion."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Standing Calf Raise", muscleGroup = "Calves", sets = 4, repsMin = 12, repsMax = 15, weight = 60.0, restSeconds = 60, notes = "2-second pause at full stretch."),

            // Thursday - Upper B
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Standing Overhead Press", muscleGroup = "Shoulders", sets = 4, repsMin = 6, repsMax = 10, weight = 50.0, restSeconds = 120, notes = "Press overhead in a straight line."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Chest-Supported Dumbbell Row", muscleGroup = "Back", sets = 4, repsMin = 8, repsMax = 12, weight = 25.0, restSeconds = 120, notes = "Squeeze shoulder blades together."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Flat Dumbbell Press", muscleGroup = "Chest", sets = 3, repsMin = 8, repsMax = 12, weight = 30.0, restSeconds = 120, notes = "Drive dumbbells toward center on press."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Lat Pulldown (Neutral Grip)", muscleGroup = "Back", sets = 3, repsMin = 8, repsMax = 12, weight = 65.0, restSeconds = 90, notes = "Pull down to upper collarbone."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Hammer Bicep Curl", muscleGroup = "Biceps", sets = 3, repsMin = 10, repsMax = 15, weight = 15.0, restSeconds = 90, notes = "Focus on brachialis and forearm development."),

            // Friday - Lower B
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Conventional Deadlift", muscleGroup = "Back", sets = 3, repsMin = 5, repsMax = 5, weight = 120.0, restSeconds = 180, notes = "Full reset each rep, do not bounce."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Bulgarian Split Squat", muscleGroup = "Quads", sets = 3, repsMin = 8, repsMax = 12, weight = 16.0, restSeconds = 90, notes = "Load front heel, maintain vertical spine."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Leg Extension", muscleGroup = "Quads", sets = 3, repsMin = 10, repsMax = 15, weight = 60.0, restSeconds = 90, notes = "Peak contraction at top."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Lying Leg Curl", muscleGroup = "Hamstrings", sets = 3, repsMin = 10, repsMax = 12, weight = 40.0, restSeconds = 90, notes = "Keep hips flat against the pad."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Seated Calf Raise", muscleGroup = "Calves", sets = 4, repsMin = 12, repsMax = 15, weight = 40.0, restSeconds = 60, notes = "Slow stretch at bottom range.")
        )

        dao.insertPlanExercises(exercises)
        selectDay("Monday")
    }
}
