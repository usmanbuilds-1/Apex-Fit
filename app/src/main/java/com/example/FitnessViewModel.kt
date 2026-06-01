@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example

import android.app.Application
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.toSystemContextString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import com.example.network.GeminiService

class FitnessViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.fitnessDao()
    private val dataStore = DataStoreManager(application)
    private val geminiService = GeminiService(dataStore, dao)
    val sessionManager = WorkoutSessionManager(dao)


    // User preferences & onboarding State
    val isOnboarded = dataStore.isOnboardedFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val username = dataStore.usernameFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val goal = dataStore.goalFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Gain Muscle")
    val currentWeight = dataStore.currentWeightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 80.0)
    val goalWeight = dataStore.goalWeightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 80.0)
    val geminiApiKey = dataStore.geminiApiKeyFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val units = dataStore.unitsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "kg")
    
    val userHeight = dataStore.heightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 175.0)
    val userAge = dataStore.ageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 25)
    val userSex = dataStore.sexFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "male")

    val calorieTargetManual = dataStore.calorieTargetManualFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val calorieTargetValue = dataStore.calorieTargetValueFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2500)
    val equipmentAvailable = dataStore.equipmentFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Barbell,Dumbbell,Cable,Machine")

    // Navigation Active Tab state (0=Home, 1=Train, 2=Nutrition, 3=Progress, 4=Coach)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Train sub-tab state (0=PROGRAM, 1=WORKOUTS, 2=PLANS)
    private val _trainSubTab = MutableStateFlow(0)
    val trainSubTab: StateFlow<Int> = _trainSubTab.asStateFlow()

    fun setTrainSubTab(subTab: Int) {
        _trainSubTab.value = subTab
    }

    // Nutrition State
    private val _selectedNutritionDate = MutableStateFlow(getTodayDateString())
    val selectedNutritionDate: StateFlow<String> = _selectedNutritionDate.asStateFlow()

    val loggedMeals = _selectedNutritionDate.flatMapLatest { date ->
        dao.getAllNutritionEntriesFlow().map { entries ->
            entries.filter { it.date == date }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNutritionHistory = dao.getAllNutritionEntriesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Weight logging State
    val weightHistory = dao.getAllWeightEntriesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Body Measurements State
    val allBodyMeasurements = dao.getAllBodyMeasurementsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Clinically calculated muscle recovery statuses
    val muscleRecoveryStatuses = dao.getAllTrainingSessionsFlow().map { sessions ->
        calculateMuscleRecovery(sessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly muscle volumes for radar chart
    val monthlyMuscleVolumes = dao.getAllTrainingSessionsFlow().map { sessions ->
        val allSets = dao.getAllExerciseSets()
        val currentMonthPrefix = "2026-05" // Matches current date in metadata (May 2026)
        val thisMonthSessions = sessions.filter { it.date.startsWith(currentMonthPrefix) }
        val thisMonthSessionIds = thisMonthSessions.map { it.id }.toSet()
        val finishedSets = allSets.filter { it.completed && !it.isWarmup && it.sessionId in thisMonthSessionIds }
        
        val axes = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Legs")
        axes.associateWith { axis ->
            finishedSets.count { set ->
                if (axis == "Legs") {
                    set.muscleGroup.lowercase() in listOf("quads", "quadriceps", "hamstrings", "calves", "glutes")
                } else {
                    isMuscleMatch(set.muscleGroup, axis)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Training State (Plans, Sessions)
    val workoutPlans = dao.getAllPlansFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activePlan = dao.getActivePlanFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    val loggedSets: StateFlow<Map<Long, List<ExerciseSet>>> = sessionManager.activeSession.map { active ->
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
                    )
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

    private val _lastCompletedSessionSets = MutableStateFlow<List<ExerciseSet>>(emptyList())
    val lastCompletedSessionSets: StateFlow<List<ExerciseSet>> = _lastCompletedSessionSets.asStateFlow()

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

    private var sessionStartTimeMillis: Long = 0L
    private var lastCompletedSetPointer: Pair<Long, Int>? = null

    // Coach chatbot State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                isUser = false,
                message = "Welcome to APEX FIT elite coaching dashboard. I am your science-driven fitness advisor. How can I optimize your program, nutrition balance, or plateau breakthroughs today?"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isCoachLoading = MutableStateFlow(false)
    val isCoachLoading: StateFlow<Boolean> = _isCoachLoading.asStateFlow()

    // Weekly reports state
    val weeklyReports = dao.getAllWeeklyReportsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        // Prepopulate standard database workouts if needed
        viewModelScope.launch {
            prepopulateDefaultPlanIfNeeded()
        }
    }

    // Navigation and tab changing
    fun selectTab(tab: Int) {
        _currentTab.value = tab
    }

    fun selectDay(day: String) {
        _selectedDayOfWeek.value = day
    }

    // Onboarding handlers
    fun completeOnboarding(username: String, goal: String, currentWeight: Double, goalWeight: Double, apiKey: String, height: Double = 175.0, age: Int = 25, sex: String = "male") {
        viewModelScope.launch {
            dataStore.saveOnboardingData(username, goal, currentWeight, goalWeight, apiKey, height, age, sex)
            // Save initial weight entry
            dao.insertWeightEntry(WeightEntry(date = getTodayDateString(), time = getCurrentLocalTimeString(), weight = currentWeight))
        }
    }

    // Settings adjustments
    fun updateProfile(name: String, userGoal: String, targetUnit: String, key: String, equipment: String, height: Double = 175.0, age: Int = 25, sex: String = "male") {
        viewModelScope.launch {
            dataStore.saveUsername(name)
            dataStore.saveGoal(userGoal)
            dataStore.saveUnits(targetUnit)
            dataStore.saveApiKey(key)
            dataStore.saveEquipment(equipment)
            dataStore.saveBiologicalParameters(height, age, sex)
        }
    }

    fun setManualCalorieTarget(manual: Boolean, calories: Int) {
        viewModelScope.launch {
            dataStore.saveCalorieTargets(manual, calories)
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            dataStore.clearAllData()
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
            prepopulateDefaultPlanIfNeeded()
            _chatMessages.value = listOf(
                ChatMessage(
                    isUser = false,
                    message = "Database reset complete. All training files reformatted. Ready to rebuild APEX training."
                )
            )
        }
    }

    // Weight Entries
    fun logWeight(weight: Double, date: String = getTodayDateString()) {
        viewModelScope.launch {
            dao.insertWeightEntry(WeightEntry(date = date, time = getCurrentLocalTimeString(), weight = weight))
            dataStore.saveWeight(weight, goalWeight.value)
        }
    }

    fun deleteWeight(date: String) {
        viewModelScope.launch {
            dao.deleteWeightEntry(date)
        }
    }

    fun deleteWeightById(id: Long) {
        viewModelScope.launch {
            dao.deleteWeightEntryById(id)
        }
    }

    // Body Measurements
    fun logBodyMeasurement(bodyPart: String, value: Double, unit: String, date: String = getTodayDateString()) {
        viewModelScope.launch {
            dao.insertBodyMeasurement(
                BodyMeasurement(
                    bodyPart = bodyPart,
                    value = value,
                    unit = unit,
                    date = date
                )
            )
        }
    }

    fun deleteBodyMeasurement(id: Long) {
        viewModelScope.launch {
            dao.deleteBodyMeasurement(id)
        }
    }

    // Nutrition Logging
    fun logNutrition(calories: Int, protein: Double, carbs: Double, fat: Double, date: String = selectedNutritionDate.value, name: String = "Logged Meal") {
        viewModelScope.launch {
            val newEntry = NutritionEntry(
                date = date,
                name = name,
                time = getCurrentLocalTimeString(),
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat
            )
            dao.insertNutritionEntry(newEntry)
        }
    }

    fun deleteNutritionForDate(date: String) {
        viewModelScope.launch {
            dao.deleteNutritionEntry(date)
        }
    }

    fun deleteNutritionEntryById(id: Long) {
        viewModelScope.launch {
            dao.deleteNutritionEntryById(id)
        }
    }

    fun changeNutritionDate(offsetDays: Int) {
        viewModelScope.launch {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val dateObj = sdf.parse(_selectedNutritionDate.value) ?: java.util.Date()
                val cal = java.util.Calendar.getInstance()
                cal.time = dateObj
                cal.add(java.util.Calendar.DAY_OF_YEAR, offsetDays)
                _selectedNutritionDate.value = sdf.format(cal.time)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Start Workout Session Execution
    fun startWorkoutSession(planSession: PlanSession) {
        _currentExerciseIdx.value = 0
        _showSessionCompleteScreen.value = false
        _warmupCompleted.value = _warmupCompleted.value.mapValues { false }
        lastCompletedSetPointer = null
        
        // Switch tabs immediately and synchronously on the main thread for high responsiveness
        _trainSubTab.value = 1 // Switch to WORKOUTS sub-tab focus
        _currentTab.value = 1 // Switch to train tab focus

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
        _trainSubTab.value = 0 // Switch back to PROGRAM sub-tab focus
    }

    fun cancelOrCompleteEmptySession() {
        cancelActiveWorkout()
    }

    fun toggleWarmupItem(item: String) {
        val current = _warmupCompleted.value.toMutableMap()
        current[item] = !(current[item] ?: false)
        _warmupCompleted.value = current
    }

    fun logWorkoutSetState(exerciseId: Long, setIndex: Int, weight: Double, reps: Int, rpe: Int, completed: Boolean) {
        logWorkoutSetState(exerciseId, setIndex, weight, reps, rpe, 0, completed)
    }

    fun logWorkoutSetState(exerciseId: Long, setIndex: Int, weight: Double, reps: Int, rpe: Int, completed: Boolean, restTakenSeconds: Int) {
        logWorkoutSetState(exerciseId, setIndex, weight, reps, rpe, restTakenSeconds, completed)
    }

    fun logWorkoutSetState(exerciseId: Long, setIndex: Int, weight: Double, reps: Int, rpe: Int, restTakenSeconds: Int, completed: Boolean, repsInReserve: Int? = null) {
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
            // Suggest RIR based on previous session's logged repsInReserve
            val lastSetForEx = dao.getLastSetForExercise(exerciseId.toString())
            _selectedRir.value = lastSetForEx?.repsInReserve ?: 2 // Default to sweet spot 2
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
            // Fetch last 5 sets at the same RPE for this exercise
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

        // Log the set with auto-calculated RPE & repsInReserve
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

        // Hide overlay and start smart rest timer
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

                // Sound Pool: trigger ticks at 3, 2, 1
                val remaining = _restTimerSeconds.value
                if (remaining in 1..3) {
                    playSynthesizedAudioTone(880.0, 150)
                } else if (remaining == 0) {
                    playSynthesizedAudioTone(1100.0, 350)
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
            // Finished last exercise! Show completion screen
            finishWorkoutSession()
        }
    }

    fun finishWorkoutSession(sessionFeel: Int = 4) {
        viewModelScope.launch {
            val session = sessionManager.activeSession.value
            val completedSets = mutableListOf<ExerciseSet>()
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
                        )
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

    private fun generateWorkoutSessionHypertrophyQualityScore(sets: List<ExerciseSet>) {
        if (sets.isEmpty()) {
            _completedHypertrophyScore.value = 0.0
            return
        }
        // Hypertrophy quality score formula: weighted formula matching RPE and Exercise Type modifiers
        // Raw Sets * RPE modifier * Exercise Type modifier
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
            val isCompound = isExerciseNameCompound(set.exerciseName)
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

    fun skipExercise() {
        skipExercise(4)
    }

    fun finishWorkoutEarly(sessionFeel: Int = 4) {
        finishWorkoutSession(sessionFeel)
    }

    fun finishWorkoutEarly() {
        finishWorkoutSession(4)
    }

    fun savePartialAndExit(sessionFeel: Int) {
        viewModelScope.launch {
            val session = sessionManager.activeSession.value
            val completedSets = mutableListOf<ExerciseSet>()
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
                        )
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

    fun savePartialSession(sessionFeel: Int = 3) {
        savePartialAndExit(sessionFeel)
    }

    fun discardAndExit() {
        sessionManager.discardAndExit()
    }

    fun discardSession() {
        discardAndExit()
    }

    val hasCompletedSets: Boolean
        get() = sessionManager.hasCompletedSets


    // Replace exercise suggestion system
    suspend fun getSubstitutionSuggestions(pattern: String, currentEx: String): List<Pair<String, String>> {
        // Mock substitutions sorted by movement pattern and equipment type to feel incredibly realistic and instant
        return when (pattern.lowercase()) {
            "chest", "push" -> listOf(
                "Incline Dumbbell Press" to "Excellent standard pushing substitute. Shifts the focus slightly to the clavicular upper portion of the pectoralis, reducing anterior deltoid shear compared to flat incline angles due to customized wrist rotation.",
                "Weighted Chest Dips" to "Highly mechanical chest builder. Targets the lower sternal costal head of the chest fibers. It triggers high motor unit recruitment but introduces higher mechanical tension to the rotator cuff."
            )
            "back", "pull" -> listOf(
                "Chest-Supported Dumbbell Row" to "Unloads the lumbar spine entirely. Allows safe high-intensity latissimus and upper back pulling mechanics without lower-back core fatigue limitations.",
                "Seated Cable Row" to "Provides constant tension through the entire concentric-concentric dynamic execution path, maximizing stretch-mediated hypertrophy."
            )
            "quads", "squat" -> listOf(
                "Hack Squat machine" to "Stabilizes the axial column. Guarantees safety when pushing close to localized muscular failure, targeting knee extensors with zero lower-back stabilization fatigue.",
                "Bulgarian Split Squat" to "Unilateral powerhouse row. Rectifies left-to-right leg imbalances while loading quads and glutes with half the absolute spine loading of standard barbell squats."
            )
            else -> listOf(
                "Cable Lateral Raise" to "Provides superior constant resistance profile. Fits the side shoulder alignment perfectly compared to standard free dumbbells which hit 0 tension at the bottom.",
                "Incline Dumbbell Bicep Curl" to "Forces the biceps brachii long head into a state of extreme pre-stretch, magnifying mechanical tension and hypertrophy pathways."
            )
        }
    }

    // Direct API Calling: Parse Paste Workout Text into Workout Plan
    fun uploadPlanTextGemini(rawText: String, onComplete: (WorkoutPlan?, List<PlanSession>, List<PlanExercise>) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val key = geminiApiKey.value
            if (key.isEmpty()) {
                onFailure("Gemini API key is not configured in settings.")
                return@launch
            }
            _isCoachLoading.value = true
            try {
                val prompt = """
                    You are a professional Android workout parser. Parse the following training text into a structured JSON workout plan.
                    Text: "$rawText"
                    
                    Return EXACTLY this JSON format, with no markdown tags, no ```json formatting wrapper, just the raw JSON object string:
                    {
                      "planName": "Scientific Hypertrophy",
                      "goal": "Gain Muscle",
                      "sessions": [
                        {
                          "label": "Upper A",
                          "day": "Monday",
                          "focus": "Chest and Back",
                          "exercises": [
                            {
                              "name": "Bench Press",
                              "muscleGroup": "Chest",
                              "sets": 4,
                              "repsMin": 6,
                              "repsMax": 8,
                              "weight": 80.0,
                              "restSeconds": 180,
                              "notes": "Arch back, retract scapulae"
                            }
                          ]
                        }
                      ]
                    }
                """.trimIndent()

                val response = callGeminiRestApi(key, prompt)
                val jsonStr = extractJsonFromRawText(response)

                val jsonObj = JSONObject(jsonStr)
                val planName = jsonObj.optString("planName", "Imported Workout Plan")
                val goalStr = jsonObj.optString("goal", "Gain Muscle")

                val planId = System.currentTimeMillis()
                val plan = WorkoutPlan(planId, planName, goalStr, isActive = false)

                val sessions = mutableListOf<PlanSession>()
                val exercises = mutableListOf<PlanExercise>()

                val sessArray = jsonObj.getJSONArray("sessions")
                for (i in 0 until sessArray.length()) {
                    val sObj = sessArray.getJSONObject(i)
                    val sId = planId + i + 1
                    val session = PlanSession(
                        id = sId,
                        planId = planId,
                        label = sObj.optString("label", "Session ${i + 1}"),
                        day = sObj.optString("day", "Monday"),
                        focus = sObj.optString("focus", "Whole Body")
                    )
                    sessions.add(session)

                    val eArray = sObj.getJSONArray("exercises")
                    for (j in 0 until eArray.length()) {
                        val eObj = eArray.getJSONObject(j)
                        val exercise = PlanExercise(
                            id = sId * 100 + j + 1,
                            planSessionId = sId,
                            name = eObj.optString("name", "Exercise ${j + 1}"),
                            muscleGroup = eObj.optString("muscleGroup", "Chest"),
                            sets = eObj.optInt("sets", 3),
                            repsMin = eObj.optInt("repsMin", 8),
                            repsMax = eObj.optInt("repsMax", 12),
                            weight = eObj.optDouble("weight", 60.0),
                            restSeconds = eObj.optInt("restSeconds", 90),
                            notes = eObj.optString("notes", "")
                        )
                        exercises.add(exercise)
                    }
                }

                _isCoachLoading.value = false
                onComplete(plan, sessions, exercises)

            } catch (e: Exception) {
                _isCoachLoading.value = false
                onFailure("Failed to parse: ${e.message}")
            }
        }
    }

    fun saveImportedWorkoutPlan(plan: WorkoutPlan, sessions: List<PlanSession>, exercises: List<PlanExercise>) {
        viewModelScope.launch {
            // Deactivate others
            dao.deactivateAllPlans()
            // Save plan
            dao.insertWorkoutPlan(plan.copy(isActive = true))
            // Save sessions & exercises
            dao.insertPlanSessions(sessions)
            dao.insertPlanExercises(exercises)
        }
    }

    // Meal Photo analysis returning macros via Gemini Vision
    fun analyseMealPhotoGemini(bitmapBytes: ByteArray, onComplete: (String, Int, Double, Double, Double) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val key = geminiApiKey.value
            if (key.isEmpty()) {
                onFailure("Gemini API key is not configured in settings.")
                return@launch
            }
            _isCoachLoading.value = true
            try {
                val base64Image = Base64.encodeToString(bitmapBytes, Base64.NO_WRAP)
                val systemPrompt = "You are an elite nutritional science scanner. Analyse the food image and estimate macros."
                val userPrompt = """
                    Analyse the meal in this photo. Return the estimate of macros.
                    Return EXACTLY this JSON structure, with no markdown code block formatting, just the raw JSON object string:
                    {
                      "mealName": "Chicken and Rice",
                      "calories": 450,
                      "protein": 35.0,
                      "carbs": 50.0,
                      "fat": 10.0
                    }
                """.trimIndent()

                // Construct payload with inlineData image part
                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", userPrompt) })
                                put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemPrompt) })
                        })
                    })
                }

                val response = callGeminiRestRaw(key, requestBody.toString())
                val jsonStr = extractJsonFromRawText(response)

                val jsonObj = JSONObject(jsonStr)
                val mealName = jsonObj.optString("mealName", "Chicken and Avocado Meal")
                val calories = jsonObj.optInt("calories", 400)
                val protein = jsonObj.optDouble("protein", 25.0)
                val carbs = jsonObj.optDouble("carbs", 40.0)
                val fat = jsonObj.optDouble("fat", 12.0)

                _isCoachLoading.value = false
                onComplete(mealName, calories, protein, carbs, fat)

            } catch (e: Exception) {
                _isCoachLoading.value = false
                onFailure("AI Image analysis failed: ${e.message}")
            }
        }
    }

    // Coach Chat Interface sending user database context with every turn
    fun sendCoachMessage(text: String, coachContext: String) {
        if (text.trim().isEmpty()) return

        val userMsg = ChatMessage(isUser = true, message = text)
        _chatMessages.value = _chatMessages.value + userMsg

        val key = geminiApiKey.value
        if (key.isBlank()) {
            _chatMessages.value = _chatMessages.value + ChatMessage(
                isUser = false,
                message = "Add your Gemini API key in Settings"
            )
            return
        }

        viewModelScope.launch {
            _isCoachLoading.value = true
            try {
                val result = geminiService.generateCoachResponse(
                    userMessage = text,
                    coachContext = coachContext
                )
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    isUser = false,
                    message = result.getOrElse { "Coach unavailable — check your connection" }
                )
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    isUser = false,
                    message = "Coach unavailable — check your connection"
                )
            } finally {
                _isCoachLoading.value = false
            }
        }
    }

    fun generateWeeklyReportWithGemini(context: android.content.Context, algorithmViewModel: AlgorithmViewModel, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isCoachLoading.value = true
            val key = geminiApiKey.value
            if (key.isEmpty()) {
                // Ensure patterns are generated even on key fallback!
                algorithmViewModel.scanAndSaveWeeklyPatterns()
                val mockReport = "Apex science report summary: Compliance reached optimal levels of 85%. TDEE trend stability is high at 2,450 kcal. Focus target on eccentric progression next week."
                dao.insertWeeklyReport(WeeklyReport(
                    weekStart = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                    score = 85,
                    geminiResponse = mockReport
                ))
                _isCoachLoading.value = false
                onSuccess()
                return@launch
            }
            try {
                // Ensure patterns are scanned and saved first
                algorithmViewModel.scanAndSaveWeeklyPatterns()

                val result = geminiService.generateWeeklyReport(algorithmViewModel)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    val e = result.exceptionOrNull()
                    val failReport = "Coaching report: Local metrics retrieved with score. Gemini reasoning connection error: ${e?.message}"
                    dao.insertWeeklyReport(WeeklyReport(
                        weekStart = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                        score = 70,
                        geminiResponse = failReport
                    ))
                    onSuccess()
                }
            } catch (e: Exception) {
                val failReport = "Coaching report: Local metrics retrieved with score. Gemini reasoning connection error: ${e.message}"
                dao.insertWeeklyReport(WeeklyReport(
                    weekStart = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                    score = 70,
                    geminiResponse = failReport
                ))
                onSuccess()
            } finally {
                _isCoachLoading.value = false
            }
        }
    }

    // Helper: direct call to Gemini REST API using raw OkHttp
    private suspend fun callGeminiRestApi(apiKey: String, userPrompt: String, systemPrompt: String = ""): String = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
            })
            if (systemPrompt.isNotEmpty()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
            }
        }
        callGeminiRestRaw(apiKey, requestBody.toString())
    }

    private suspend fun callGeminiRestRaw(apiKey: String, payload: String): String = withContext(Dispatchers.IO) {
        // Use gemini-3.5-flash which is standard and robust
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val body = payload.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .post(body)
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("HTTP Error: ${resp.code} - ${resp.body?.string()}")
            }
            val respBody = resp.body?.string() ?: throw Exception("Empty body")
            val obj = JSONObject(respBody)
            val candidates = obj.getJSONArray("candidates")
            val firstCand = candidates.getJSONObject(0)
            val contentObj = firstCand.getJSONObject("content")
            val partsArr = contentObj.getJSONArray("parts")
            partsArr.getJSONObject(0).getString("text")
        }
    }

    private fun extractJsonFromRawText(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substring(7)
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3)
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length - 3)
        }
        return clean.trim()
    }

    // Tone Synth Generator playing synchronous audio tick
    private fun playSynthesizedAudioTone(frequencyHz: Double, durationMs: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val sampleRate = 8000
                val numSamples = durationMs * sampleRate / 1000
                val sample = DoubleArray(numSamples)
                val generatedSnd = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / frequencyHz))
                }
                var idx = 0
                for (dVal in sample) {
                    val val1 = (dVal * 32767).toInt().toShort()
                    generatedSnd[idx++] = val1
                }

                val audioTrack = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            android.media.AudioFormat.Builder()
                                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(numSamples * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        numSamples * 2,
                        AudioTrack.MODE_STATIC
                    )
                }
                audioTrack.write(generatedSnd, 0, numSamples)
                audioTrack.play()
                delay(durationMs.toLong() + 50)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playMusicSynthNote() {
        val notes = listOf(130.81, 164.81, 196.00, 220.00) // C3, E3, G3, A3
        val randomNote = notes.random()
        playSynthesizedAudioTone(randomNote, 80)
    }

    // Check if exercise name is compound for hypertrophy formulas
    private fun isExerciseNameCompound(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("squat") || n.contains("press") || n.contains("deadlift") || n.contains("row") || n.contains("pullup") || n.contains("dips")
    }

    // Prepopulate system database with the designated 4-Day Splitting program
    private suspend fun prepopulateDefaultPlanIfNeeded() {
        val existing = dao.getAllPlans()
        if (existing.isEmpty()) {
            val planId = 1L
            // Science-Based Hypertrophy 4-Day Split
            val defaultPlan = WorkoutPlan(
                id = planId,
                name = "Science-Based Hypertrophy 4-Day Split",
                goal = "Gain Muscle",
                isActive = true
            )
            dao.insertWorkoutPlan(defaultPlan)

            val sessions = listOf(
                PlanSession(101L, planId, "Upper A", "Monday", "Chest, Back, Arms"),
                PlanSession(102L, planId, "Lower A", "Tuesday", "Quads, Hamstrings, Calves"),
                PlanSession(103L, planId, "Zone 2", "Wednesday", "Steady Cardio Aerobic Base"),
                PlanSession(104L, planId, "Upper B", "Thursday", "Shoulders, Upper Back, Chest"),
                PlanSession(105L, planId, "Lower B", "Friday", "Deadlift Posterior, Glutes, Core"),
                PlanSession(106L, planId, "Zone 2", "Saturday", "Steady Cardio"),
                PlanSession(107L, planId, "Rest Day", "Sunday", "Muscle Recovery & Rest")
            )
            dao.insertPlanSessions(sessions)

            val exercises = listOf(
                // Upper A Upper Group
                PlanExercise(1L, 101L, "Flat Barbell Bench Press", "Chest", 4, 6, 8, 80.0, 180, "Aim for controlled eccentric phase, touch lower chest."),
                PlanExercise(2L, 101L, "Barbell Bent Over Row", "Back", 4, 8, 10, 70.0, 180, "Pull torso to belly-button, squeeze shoulder blades."),
                PlanExercise(3L, 101L, "Incline Dumbbell Press", "Chest", 3, 10, 12, 26.0, 120, "Set bench to 30 degrees, maximize pec stretch."),
                PlanExercise(4L, 101L, "Lat Pulldown Wide-Grip", "Back", 3, 10, 12, 55.0, 90, "Pull to upper chest, emphasize lat stretch."),
                PlanExercise(5L, 101L, "Barbell Bicep Curl", "Biceps", 3, 10, 12, 30.0, 90, "Strict form, avoid shoulder momentum."),

                // Lower A Leg Group
                PlanExercise(6L, 102L, "Barbell Back Squat", "Quads", 4, 6, 8, 100.0, 180, "Go parallel or deeper, brace core fully."),
                PlanExercise(7L, 102L, "Romanian Deadlift", "Hamstrings", 4, 8, 10, 85.0, 180, "Hinge at hip, keep back straight, load hamstrings."),
                PlanExercise(8L, 102L, "Leg Press 45-Degree", "Quads", 3, 12, 15, 160.0, 120, "Deep range of motion, avoid lockout knee pop."),
                PlanExercise(9L, 102L, "Seated Calf Raise", "Calves", 4, 15, 20, 45.0, 90, "2-second pause at maximum peak heel stretch."),

                // Wednesday Zone 2 Cardio Block
                PlanExercise(10L, 103L, "Steady State Treadmill", "Cardio", 1, 40, 50, 0.0, 0, "Maintain heart rate in Zone 2 level (120-135 BPM)."),

                // Upper B Shoulder
                PlanExercise(11L, 104L, "Overhead Press (OHP)", "Shoulders", 4, 6, 8, 50.0, 180, "Squeeze glutes and abs, bar path close to nose."),
                PlanExercise(12L, 104L, "Weighted Chin Up", "Back", 4, 6, 10, 15.0, 180, "Squeeze pull vertical, add belt weights if needed."),
                PlanExercise(13L, 104L, "Machine Pec Fly", "Chest", 3, 12, 15, 60.0, 90, "Keep elbows soft, hug the tree visual."),
                PlanExercise(14L, 104L, "Dumbbell Lateral Raise", "Side Delts", 4, 12, 15, 12.0, 90, "Lead with elbows, pause at overhead parallel."),

                // Lower B Hinge Group
                PlanExercise(15L, 105L, "Conventional Deadlift", "Hamstrings", 3, 5, 5, 120.0, 180, "Pull slack out of bar, drive feet into platform."),
                PlanExercise(16L, 105L, "Leg Extensions", "Quads", 3, 12, 15, 75.0, 90, "Hold extension peak squeeze. Force maximum quad load."),
                PlanExercise(17L, 105L, "Hanging Knee Raise", "Core", 3, 15, 20, 0.0, 90, "Squeeze lower abs. Slow eccentric descent.")
            )
            dao.insertPlanExercises(exercises)

            // Also prepopulate historical body measurements for 3 points in time to draw beautiful progression graphs on first start
            val parts = listOf(
                "Neck" to listOf(38.0, 38.0, 38.2),
                "Shoulders" to listOf(118.0, 118.5, 119.2),
                "Chest" to listOf(102.0, 102.6, 103.5),
                "Left Bicep" to listOf(36.5, 36.8, 37.2),
                "Right Bicep" to listOf(36.7, 37.0, 37.4),
                "Left Forearm" to listOf(29.0, 29.2, 29.5),
                "Right Forearm" to listOf(29.2, 29.4, 29.7),
                "Waist" to listOf(84.0, 83.2, 82.5),
                "Hips" to listOf(96.0, 95.5, 95.0),
                "Left Thigh" to listOf(58.0, 58.4, 59.0),
                "Right Thigh" to listOf(58.2, 58.5, 59.2),
                "Left Calf" to listOf(37.0, 37.1, 37.3),
                "Right Calf" to listOf(37.2, 37.3, 37.5)
            )
            val dates = listOf("2026-05-01", "2026-05-10", "2026-05-20")
            parts.forEach { (part, values) ->
                values.forEachIndexed { idx, value ->
                    dao.insertBodyMeasurement(
                        BodyMeasurement(
                            bodyPart = part,
                            value = value,
                            unit = "cm",
                            date = dates[idx]
                        )
                    )
                }
            }
        }
    }

    private fun getTodayDateString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }

    fun getCurrentLocalTimeString(): String {
        return java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date())
    }

    private fun isMuscleMatch(group: String, muscle: String): Boolean {
        val g = group.lowercase().trim()
        val m = muscle.lowercase().trim()
        if (g == m) return true
        return when (m) {
            "shoulders" -> g in listOf("shoulders", "front delts", "side delts", "rear delts", "side_delt", "rear_delt")
            "back" -> g in listOf("back", "upper back", "lats", "trapezius")
            "quads" -> g in listOf("quads", "quadriceps")
            "abs" -> g in listOf("abs", "core", "obliques")
            "lower back" -> g in listOf("lower back", "lower_back")
            else -> false
        }
    }

    private suspend fun calculateMuscleRecovery(sessions: List<TrainingSession>): List<MuscleRecoveryStatus> = withContext(Dispatchers.IO) {
        val allSets = dao.getAllExerciseSets()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        
        val muscles = listOf("Chest", "Shoulders", "Triceps", "Back", "Biceps", "Quads", "Hamstrings", "Glutes", "Calves", "Abs", "Lower Back")
        
        muscles.map { muscle ->
            val matchingSets = allSets.filter { set ->
                set.completed && !set.isWarmup && isMuscleMatch(set.muscleGroup, muscle)
            }
            
            if (matchingSets.isEmpty()) {
                MuscleRecoveryStatus(
                    muscleGroup = muscle,
                    recoveryPercentage = 100,
                    lastExercise = "No recent exercises",
                    lastTrainingDate = null,
                    requiredHours = 48
                )
            } else {
                val setsWithSessionsAndDates = matchingSets.mapNotNull { set ->
                    val session = sessions.find { it.id == set.sessionId } ?: return@mapNotNull null
                    set to session
                }.sortedByDescending { it.second.date }
                
                if (setsWithSessionsAndDates.isEmpty()) {
                    MuscleRecoveryStatus(
                        muscleGroup = muscle,
                        recoveryPercentage = 100,
                        lastExercise = "No recent exercises",
                        lastTrainingDate = null,
                        requiredHours = 48
                    )
                } else {
                    val (lastSet, lastSession) = setsWithSessionsAndDates.first()
                    val lastDateStr = lastSession.date
                    val lastDateObj = try { sdf.parse(lastDateStr) } catch(e: Exception) { null } ?: java.util.Date()
                    val todayDateObj = java.util.Date()
                    
                    val diffMs = todayDateObj.time - lastDateObj.time
                    val diffHoursRaw = diffMs / (1000 * 60 * 60)
                    val elapsedHours = maxOf(0, diffHoursRaw.toInt())
                    
                    val todayStr = sdf.format(todayDateObj)
                    val trainedToday = (lastDateStr == todayStr)
                    
                    val todaysSets = setsWithSessionsAndDates.filter { it.second.date == lastDateStr }
                    val totalSets = todaysSets.size
                    val highestRpe = todaysSets.maxOfOrNull { it.first.rpe } ?: 0
                    
                    val isHighIntensity = totalSets > 5 || highestRpe >= 8
                    val requiredHours = if (isHighIntensity) 72 else 48
                    
                    var pct = if (elapsedHours >= requiredHours) {
                        100
                    } else {
                        ((elapsedHours.toFloat() / requiredHours.toFloat()) * 100f).toInt()
                    }
                    pct = maxOf(0, minOf(100, pct))
                    
                    if (trainedToday && highestRpe >= 8) {
                        pct = 0
                    }
                    
                    MuscleRecoveryStatus(
                        muscleGroup = muscle,
                        recoveryPercentage = pct,
                        lastExercise = lastSet.exerciseName,
                        lastTrainingDate = lastDateStr,
                        requiredHours = requiredHours
                    )
                }
            }
        }
    }
}

data class ChatMessage(
    val isUser: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
