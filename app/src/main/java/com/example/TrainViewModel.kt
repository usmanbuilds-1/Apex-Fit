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

class TrainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.fitnessDao()
    private val dataStore = DataStoreManager(application)
    private val repository: FitnessRepository = FitnessRepositoryImpl(dao, dataStore)
    val sessionManager = WorkoutSessionManager(repository)

    // User weight from preferences for relative calculations
    val currentWeight = dataStore.currentWeightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 80.0)
    val userHeight = dataStore.heightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 175.0)
    val units = dataStore.unitsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "kg")

    // Training State (Plans, Sessions)
    val workoutPlans = dao.getAllPlansFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activePlan = repository.getActivePlan().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedDayOfWeek = MutableStateFlow("Monday")
    val selectedDayOfWeek: StateFlow<String> = _selectedDayOfWeek.asStateFlow()

    val activePlanSessions = activePlan.flatMapLatest { plan ->
        if (plan != null) dao.getSessionsForPlanFlow(plan.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedDaySession = combine(activePlanSessions, _selectedDayOfWeek) { sessions, day ->
        sessions.firstOrNull { it.day.equals(day, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedDayExercises = selectedDaySession.flatMapLatest { session ->
        if (session != null) dao.getExercisesForSessionFlow(session.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayExercises = activePlanSessions.flatMapLatest { sessions ->
        val todayDayString = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
        val todaySession = sessions.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
        if (todaySession != null) dao.getExercisesForSessionFlow(todaySession.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDay(day: String) {
        _selectedDayOfWeek.value = day
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

        viewModelScope.launch {
            var elapsed = 0
            while (_restTimerSeconds.value > 0 && _isRestTimerActive.value) {
                delay(1000)
                _restTimerSeconds.value -= 1
                elapsed += 1

                lastCompletedSetPointer?.let { (exId, sIdx) ->
                    updateSetRestTaken(exId, sIdx, elapsed)
                }

                val remaining = _restTimerSeconds.value
                if (remaining in 1..3) {
                    AudioService.playBeep()
                } else if (remaining == 0) {
                    AudioService.playRestTimerComplete()
                }
            }
            if (_isRestTimerActive.value) {
                closeRestTimer()
            }
        }
    }

    fun addRestTimerSeconds(secs: Int) {
        _restTimerSeconds.value += secs
        _restTimerTotal.value += secs
    }

    fun closeRestTimer() {
        _isRestTimerActive.value = false
        _showRestOverlay.value = false
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

            _lastCompletedSessionSets.value = completedSets
            val result = sessionManager.commitToDatabase(sessionFeel)
            _completedStats.value = result.durationMinutes to (result.totalVolumeTonnes * 1000.0)

            _completedPRsBroken.value = result.confirmedPRs.map { exerciseId ->
                val exerciseName = session?.exercises?.find { it.exerciseId == exerciseId }?.exerciseName ?: "Exercise"
                "$exerciseName PR Conquered!"
            }

            generateWorkoutSessionHypertrophyQualityScore(completedSets)
            _showSessionCompleteScreen.value = true
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

            _lastCompletedSessionSets.value = completedSets
            val result = sessionManager.savePartialAndExit(sessionFeel)
            _completedStats.value = result.durationMinutes to (result.totalVolumeTonnes * 1000.0)
            
            _completedPRsBroken.value = result.confirmedPRs.map { exerciseId ->
                val exerciseName = session?.exercises?.find { it.exerciseId == exerciseId }?.exerciseName ?: "Exercise"
                "$exerciseName PR Conquered!"
            }

            generateWorkoutSessionHypertrophyQualityScore(completedSets)
            _showSessionCompleteScreen.value = true
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

    private val geminiService = com.example.network.GeminiService(dataStore, repository)

    fun uploadPlanTextGemini(
        rawText: String,
        onComplete: (WorkoutPlan, List<PlanSession>, List<PlanExercise>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = geminiService.parseWorkoutPlan(rawText)
                if (result.isSuccess) {
                    val parsedDays = result.getOrThrow()
                    val planId = System.currentTimeMillis()
                    val plan = WorkoutPlan(
                        id = planId,
                        name = "AI Imported Split",
                        goal = "Gain Muscle",
                        isActive = true,
                        createdAt = System.currentTimeMillis()
                    )
                    val sessions = mutableListOf<PlanSession>()
                    val exercises = mutableListOf<PlanExercise>()

                    parsedDays.forEachIndexed { sIdx, day ->
                        val sessionId = planId + 100 + sIdx
                        sessions.add(
                            PlanSession(
                                id = sessionId,
                                planId = planId,
                                label = day.label,
                                day = day.day,
                                focus = day.label
                            )
                        )
                        day.exercises.forEachIndexed { eIdx, ex ->
                            val exerciseId = sessionId * 100 + eIdx
                            exercises.add(
                                PlanExercise(
                                    id = exerciseId,
                                    planSessionId = sessionId,
                                    name = ex.name,
                                    muscleGroup = ex.muscleGroup,
                                    sets = ex.sets,
                                    repsMin = ex.repsMin,
                                    repsMax = ex.repsMax,
                                    weight = 0.0,
                                    restSeconds = ex.restSeconds,
                                    notes = ""
                                )
                            )
                        }
                    }
                    onComplete(plan, sessions, exercises)
                } else {
                    onFailure(result.exceptionOrNull()?.message ?: "Unknown AI analysis failure")
                }
            } catch (e: Exception) {
                onFailure(e.message ?: "Unknown error")
            }
        }
    }

    fun saveImportedWorkoutPlan(
        plan: WorkoutPlan,
        sessions: List<PlanSession>,
        exercises: List<PlanExercise>
    ) {
        viewModelScope.launch {
            dao.deactivateAllPlans()
            dao.insertWorkoutPlan(plan)
            sessions.forEach { dao.insertPlanSession(it) }
            exercises.forEach { dao.insertPlanExercise(it) }
        }
    }
}
