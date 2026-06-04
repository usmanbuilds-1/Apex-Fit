@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.repository.FitnessRepository
import com.example.data.repository.FitnessRepositoryImpl
import com.example.ui.models.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FitnessViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.fitnessDao()
    private val dataStore = DataStoreManager(application)
    private val repository: FitnessRepository = FitnessRepositoryImpl(dao, dataStore)

    // Instantiate newly created specialized ViewModels
    val homeVM = HomeViewModel(application)
    val trainVM = TrainViewModel(application)
    val progressVM = ProgressViewModel(application)
    val nutritionVM = NutritionViewModel(application)
    val coachVM = CoachViewModel(application)

    // User preferences & onboarding State (Expose from preferences)
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

    fun selectTab(tab: Int) {
        _currentTab.value = tab
    }

    fun completeOnboarding(username: String, goal: String, currentWeight: Double, goalWeight: Double, apiKey: String, height: Double = 175.0, age: Int = 25, sex: String = "male") {
        viewModelScope.launch {
            dataStore.saveOnboardingData(username, goal, currentWeight, goalWeight, apiKey, height, age, sex)
        }
    }

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

    // ─────────────────────────────────────────────────────────────────
    // Delegated properties representing state flows relocated to specialized ViewModels
    // ─────────────────────────────────────────────────────────────────

    // Home dashboard delegated flows
    val weightHistory: StateFlow<List<UiWeightEntry>> = homeVM.weightHistory
    val allNutritionHistory: StateFlow<List<UiNutritionEntry>> = homeVM.allNutritionHistory

    // Training delegated flows from TrainViewModel
    val workoutPlans: StateFlow<List<WorkoutPlan>> = trainVM.workoutPlans
    val activePlan: StateFlow<WorkoutPlan?> = trainVM.activePlan
    val selectedDayOfWeek: StateFlow<String> = trainVM.selectedDayOfWeek
    val activePlanSessions: StateFlow<List<PlanSession>> = trainVM.activePlanSessions
    val selectedDaySession: StateFlow<PlanSession?> = trainVM.selectedDaySession
    val selectedDayExercises: StateFlow<List<PlanExercise>> = trainVM.selectedDayExercises
    val todayExercises: StateFlow<List<PlanExercise>> = trainVM.todayExercises

    val activeSession: StateFlow<com.example.data.ActiveSession?> = trainVM.activeSession
    val lastWeights: StateFlow<Map<String, Double>> = trainVM.lastWeights
    val pendingPRWarnings: StateFlow<Map<String, Double>> = trainVM.pendingPRWarnings
    val activeWorkoutSession: StateFlow<PlanSession?> = trainVM.activeWorkoutSession
    val activeExercises: StateFlow<List<PlanExercise>> = trainVM.activeExercises
    val currentExerciseIdx: StateFlow<Int> = trainVM.currentExerciseIdx
    val loggedSets: StateFlow<Map<Long, List<UiExerciseSet>>> = trainVM.loggedSets
    val weightContextLines: StateFlow<Map<String, String>> = trainVM.weightContextLines
    val effectiveSetsStateFlow: StateFlow<Map<String, EffectiveSetsData>> = trainVM.effectiveSetsStateFlow

    val warmupCompleted: StateFlow<Map<String, Boolean>> = trainVM.warmupCompleted
    val lastCompletedSessionSets: StateFlow<List<UiExerciseSet>> = trainVM.lastCompletedSessionSets

    val restTimerSeconds: StateFlow<Int> = trainVM.restTimerSeconds
    val restTimerTotal: StateFlow<Int> = trainVM.restTimerTotal
    val isRestTimerActive: StateFlow<Boolean> = trainVM.isRestTimerActive
    val showRestOverlay: StateFlow<Boolean> = trainVM.showRestOverlay
    val restTimerLastSetContext: StateFlow<String> = trainVM.restTimerLastSetContext
    val restTimerNextSetPreview: StateFlow<String> = trainVM.restTimerNextSetPreview

    val showSessionCompleteScreen: StateFlow<Boolean> = trainVM.showSessionCompleteScreen
    val completedStats: StateFlow<Pair<Int, Double>> = trainVM.completedStats
    val completedPRsBroken: StateFlow<List<String>> = trainVM.completedPRsBroken

    val showRirOverlay: StateFlow<Boolean> = trainVM.showRirOverlay
    val isShowingRirHistory: StateFlow<Boolean> = trainVM.isShowingRirHistory
    val rirSelectorExerciseId: StateFlow<Long> = trainVM.rirSelectorExerciseId
    val rirSelectorExerciseName: StateFlow<String> = trainVM.rirSelectorExerciseName
    val rirSelectorMuscleGroup: StateFlow<String> = trainVM.rirSelectorMuscleGroup
    val rirSelectorSetIndex: StateFlow<Int> = trainVM.rirSelectorSetIndex
    val rirSelectorWeight: StateFlow<Double> = trainVM.rirSelectorWeight
    val rirSelectorReps: StateFlow<Int> = trainVM.rirSelectorReps
    val rirSelectorTotalSets: StateFlow<Int> = trainVM.rirSelectorTotalSets
    val selectedRir: StateFlow<Int> = trainVM.selectedRir
    val rirHistoricalSets: StateFlow<List<com.example.data.LastSetWithDate>> = trainVM.rirHistoricalSets
    val completedHypertrophyScore: StateFlow<Double> = trainVM.completedHypertrophyScore

    val estimatedSetDuration: StateFlow<(Int, Int) -> Int> = flowOf { repsMin: Int, repsMax: Int ->
        com.example.utils.AlgorithmEngine.estimateSetDurationMinutes(repsMin, repsMax).toInt()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = { _, _ -> 3 }
    )

    // Progress and Analytics delegated flows from ProgressViewModel
    val allBodyMeasurements: StateFlow<List<UiBodyMeasurement>> = progressVM.allBodyMeasurements
    val trendWeight: StateFlow<Double?> = progressVM.trendWeight
    val monthlyMuscleVolumes: StateFlow<Map<String, Int>> = progressVM.monthlyMuscleVolumes
    val muscleRecoveryStatuses: StateFlow<List<MuscleRecoveryStatus>> = progressVM.muscleRecoveryStatuses
    val fatigueRatio: StateFlow<FatigueRatio> = progressVM.fatigueRatio

    // Nutrition delegated flows from NutritionViewModel
    val selectedNutritionDate: StateFlow<String> = nutritionVM.selectedNutritionDate
    val loggedMeals: StateFlow<List<UiNutritionEntry>> = nutritionVM.loggedMeals
    val suggestedCaloricTarget: StateFlow<Int> = nutritionVM.suggestedCaloricTarget
    val waterIntakeToday: StateFlow<Int> = nutritionVM.waterIntakeToday

    // Coach delegated flows from CoachViewModel
    val chatMessages: StateFlow<List<ChatMessage>> = coachVM.chatMessages
    val isCoachLoading: StateFlow<Boolean> = coachVM.isCoachLoading
    val weeklyReports: StateFlow<List<WeeklyReport>> = coachVM.weeklyReports

    // ─────────────────────────────────────────────────────────────────
    // Delegated actions and methods relocated to specialized ViewModels
    // ─────────────────────────────────────────────────────────────────

    // Training methods
    fun startWorkoutSession(planSession: PlanSession) {
        trainVM.startWorkoutSession(planSession)
    }

    fun selectDay(day: String) {
        trainVM.selectDay(day)
    }

    fun cancelActiveWorkout() {
        trainVM.cancelActiveWorkout()
    }

    fun toggleWarmupItem(item: String) {
        trainVM.toggleWarmupItem(item)
    }

    fun logWorkoutSetState(exerciseId: Long, setIndex: Int, weight: Double, reps: Int, rpe: Int, completed: Boolean, restTakenSeconds: Int = 0, repsInReserve: Int? = null) {
        trainVM.logWorkoutSetState(exerciseId, setIndex, weight, reps, rpe, completed, restTakenSeconds, repsInReserve)
    }

    fun addCustomLogSet(exerciseId: Long) {
        trainVM.addCustomLogSet(exerciseId)
    }

    fun openRirSelector(exerciseId: Long, exerciseName: String, muscleGroup: String, setIndex: Int, weight: Double, reps: Int, totalSets: Int) {
        trainVM.openRirSelector(exerciseId, exerciseName, muscleGroup, setIndex, weight, reps, totalSets)
    }

    fun selectRirOption(rir: Int) {
        trainVM.selectRirOption(rir)
    }

    fun confirmRirSelection() {
        trainVM.confirmRirSelection()
    }

    fun confirmRirAndNextSet() {
        trainVM.confirmRirAndNextSet()
    }

    fun closeRirSelector() {
        trainVM.closeRirSelector()
    }

    fun addRestTimerSeconds(secs: Int) {
        trainVM.addRestTimerSeconds(secs)
    }

    fun closeRestTimer() {
        trainVM.closeRestTimer()
    }

    fun nextExercise() {
        trainVM.nextExercise()
    }

    fun finishWorkoutSession(sessionFeel: Int = 4) {
        trainVM.finishWorkoutSession(sessionFeel)
    }

    fun dismissSessionComplete() {
        trainVM.dismissSessionComplete()
    }

    fun skipExercise(sessionFeel: Int = 4) {
        trainVM.skipExercise(sessionFeel)
    }

    fun finishWorkoutEarly(sessionFeel: Int = 4) {
        trainVM.finishWorkoutEarly(sessionFeel)
    }

    fun savePartialAndExit(sessionFeel: Int) {
        trainVM.savePartialAndExit(sessionFeel)
    }

    val hasCompletedSets: Boolean
        get() = trainVM.hasCompletedSets

    suspend fun getSubstitutionSuggestions(pattern: String, currentEx: String): List<Pair<String, String>> {
        return trainVM.getSubstitutionSuggestions(pattern, currentEx)
    }

    // Weight and measurements methods delegated to HomeViewModel & ProgressViewModel
    fun logWeight(weight: Double, date: String = getTodayDateString()) {
        homeVM.logWeight(weight, date)
    }

    fun logBodyMeasurement(bodyPart: String, value: Double, unit: String, date: String = getTodayDateString()) {
        progressVM.logBodyMeasurement(bodyPart, value, unit, date)
    }

    fun deleteBodyMeasurement(id: Long) {
        progressVM.deleteBodyMeasurement(id)
    }

    // Nutrition methods delegated to NutritionViewModel
    fun logNutrition(calories: Int, protein: Double, carbs: Double, fat: Double, date: String = getTodayDateString(), name: String = "Logged Meal") {
        nutritionVM.logNutrition(calories, protein, carbs, fat, date, name)
    }

    fun deleteNutritionEntryById(id: Long) {
        nutritionVM.deleteNutritionEntryById(id)
    }

    fun changeNutritionDate(offsetDays: Int) {
        nutritionVM.changeNutritionDate(offsetDays)
    }

    fun logWater(ml: Int) {
        nutritionVM.logWater(ml)
    }

    fun resetWater() {
        nutritionVM.resetWater()
    }

    // Coach methods delegated to CoachViewModel
    fun sendCoachMessage(text: String, coachContext: String) {
        coachVM.sendCoachMessage(text, coachContext)
    }

    fun generateWeeklyReportWithGemini(context: android.content.Context, algorithmViewModel: AlgorithmViewModel, onSuccess: () -> Unit) {
        coachVM.generateWeeklyReportWithGemini(context, algorithmViewModel, onSuccess)
    }

    fun clearChat() {
        coachVM.clearChat()
    }

    // Helpers & Extra Delegators
    fun discardAndExit() {
        trainVM.cancelActiveWorkout()
    }

    fun cancelOrCompleteEmptySession() {
        if (hasCompletedSets) {
            savePartialAndExit(4)
        } else {
            discardAndExit()
        }
    }

    fun playMusicSynthNote() {
        com.example.utils.AudioService.playMusicSynthNote()
    }

    fun deleteWeightById(id: Long) {
        homeVM.deleteWeightById(id)
    }

    fun deleteWeight(date: String) {
        homeVM.deleteWeight(date)
    }

    fun analyseMealPhotoGemini(
        bitmapBytes: ByteArray,
        onComplete: (String, Int, Double, Double, Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        nutritionVM.analyseMealPhotoGemini(bitmapBytes, onComplete, onFailure)
    }

    fun resetAllData() {
        viewModelScope.launch {
            db.clearAllTables()
            dataStore.clearAllData()
        }
    }

    fun getCurrentLocalTimeString(): String {
        return java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date())
    }

    fun uploadPlanTextGemini(
        rawText: String,
        onComplete: (WorkoutPlan, List<PlanSession>, List<PlanExercise>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            coachVM.setCoachLoading(true)
            trainVM.uploadPlanTextGemini(
                rawText,
                onComplete = { p, s, e ->
                    coachVM.setCoachLoading(false)
                    onComplete(p, s, e)
                },
                onFailure = { err ->
                    coachVM.setCoachLoading(false)
                    onFailure(err)
                }
            )
        }
    }

    fun saveImportedWorkoutPlan(
        plan: WorkoutPlan,
        sessions: List<PlanSession>,
        exercises: List<PlanExercise>
    ) {
        trainVM.saveImportedWorkoutPlan(plan, sessions, exercises)
    }

    fun getDateDaysAgo(daysAgo: Int): String {
        return com.example.utils.AlgorithmEngine.getDateDaysAgo(daysAgo)
    }

    private fun getTodayDateString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }
}
