@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.apexfit.app.data.*
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.data.repository.FitnessRepositoryImpl
import com.apexfit.app.ui.models.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class FitnessViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val db = com.apexfit.app.di.ServiceLocator.database(application)
    private val dao = db.fitnessDao()
    private val dataStore = com.apexfit.app.di.ServiceLocator.dataStore(application)
    private val repository = com.apexfit.app.di.ServiceLocator.repository(application)

    private val childStore = ViewModelStore()
    private val childFactory = ViewModelProvider.AndroidViewModelFactory(application)

    val homeVM: HomeViewModel by lazy {
        ViewModelProvider(childStore, childFactory)[HomeViewModel::class.java]
    }
    val trainVM: TrainViewModel by lazy {
        ViewModelProvider(childStore, childFactory)[TrainViewModel::class.java]
    }
    val progressVM: ProgressViewModel by lazy {
        ViewModelProvider(childStore, childFactory)[ProgressViewModel::class.java]
    }
    val nutritionVM: NutritionViewModel by lazy {
        ViewModelProvider(childStore, childFactory)[NutritionViewModel::class.java]
    }

    // User preferences & onboarding State (Expose from preferences)
    val isOnboarded: StateFlow<Boolean?> = dataStore.isOnboardedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), null)
    val username = dataStore.usernameFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), "")
    val goal = dataStore.goalFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), "Gain Muscle")
    val currentWeight = dataStore.currentWeightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), com.apexfit.app.UserDefaults.WEIGHT_KG)
    val goalWeight = dataStore.goalWeightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), com.apexfit.app.UserDefaults.WEIGHT_KG)
    val units = dataStore.unitsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), "kg")
    
    val userHeight = dataStore.heightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), com.apexfit.app.UserDefaults.HEIGHT_CM)
    val userAge = dataStore.ageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), com.apexfit.app.UserDefaults.AGE_YEARS)
    val userSex = dataStore.sexFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), "male")
    val weeklyWorkouts = dataStore.weeklyWorkoutsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), 3)

    val calorieTargetManual = dataStore.calorieTargetManualFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), false)
    val calorieTargetValue = dataStore.calorieTargetValueFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), com.apexfit.app.UserDefaults.CALORIES)
    val equipmentAvailable = dataStore.equipmentFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), "Barbell,Dumbbell,Cable,Machine")

    // Navigation Active Tab state (0=Home, 1=Train, 2=Nutrition, 3=Progress, 4=Coach)
    private val _currentTab = MutableStateFlow(
        savedStateHandle.get<Int>("current_tab") ?: 0
    )
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Train sub-tab state (0=PROGRAM, 1=WORKOUTS, 2=PLANS)
    private val _trainSubTab = MutableStateFlow(0)
    val trainSubTab: StateFlow<Int> = _trainSubTab.asStateFlow()

    fun setTrainSubTab(subTab: Int) {
        _trainSubTab.value = subTab
    }

    fun selectTab(tab: Int) {
        _currentTab.value = tab
        savedStateHandle["current_tab"] = tab
    }

    fun completeOnboarding(
        username: String,
        goal: String,
        currentWeight: Double,
        goalWeight: Double,
        height: Double = com.apexfit.app.UserDefaults.HEIGHT_CM,
        age: Int = com.apexfit.app.UserDefaults.AGE_YEARS,
        sex: String = "male",
        units: String = "kg",
        weeklyWorkouts: Int = 3
    ) {
        val isImperial = units.lowercase() in listOf("lb", "lbs")
        val weightKg = if (isImperial) currentWeight / 2.20462 else currentWeight
        val goalKg   = if (isImperial) goalWeight / 2.20462   else goalWeight
        val heightCm = if (isImperial) height * 2.54           else height
        val validWeight     = weightKg.coerceIn(20.0, 500.0)
        val validGoalWeight = goalKg.coerceIn(20.0, 500.0)
        val validHeight     = heightCm.coerceIn(100.0, 250.0)
        val validAge       = age.coerceIn(13, 100)
        val validGoal      = if (goal in listOf("Gain Muscle", "Lose Fat", "Maintain", "Recomposition")) goal else "Maintain"
        val validName      = username.trim().take(50).ifEmpty { "Athlete" }
        viewModelScope.launch {
            dataStore.saveOnboardingData(validName, validGoal, validWeight, validGoalWeight, validHeight, validAge, sex, weeklyWorkouts)
            dataStore.saveUnits(units)
        }
    }

    fun updateWeeklyWorkouts(n: Int) {
        viewModelScope.launch {
            dataStore.saveWeeklyWorkouts(n)
        }
    }

    fun updateProfile(name: String, userGoal: String, targetUnit: String, equipment: String, height: Double = com.apexfit.app.UserDefaults.HEIGHT_CM, age: Int = com.apexfit.app.UserDefaults.AGE_YEARS, sex: String = "male") {
        val validHeight    = height.coerceIn(100.0, 250.0)
        val validAge       = age.coerceIn(13, 100)
        val validGoal      = if (userGoal in listOf("Gain Muscle", "Lose Fat", "Maintain", "Recomposition")) userGoal else "Maintain"
        val validName      = name.trim().take(50).ifEmpty { "Athlete" }
        viewModelScope.launch {
            dataStore.saveUsername(validName)
            dataStore.saveGoal(validGoal)
            dataStore.saveUnits(targetUnit)
            dataStore.saveEquipment(equipment)
            dataStore.saveBiologicalParameters(validHeight, validAge, sex)
        }
    }

    fun setManualCalorieTarget(manual: Boolean, calories: Int) {
        viewModelScope.launch {
            dataStore.saveCalorieTargets(manual, calories)
        }
    }

    fun setPreferredUnits(targetUnits: String) {
        viewModelScope.launch {
            dataStore.saveUnits(targetUnits)
        }
    }

    fun setGoalWeight(value: Double, preferredUnit: String = "kg") {
        viewModelScope.launch {
            val valueKg = if (preferredUnit.lowercase() in listOf("lb", "lbs")) value / 2.20462 else value
            dataStore.saveWeight(currentWeight.value, valueKg)
        }
    }

    fun setBodyWeight(value: Double, preferredUnit: String = "kg") {
        viewModelScope.launch {
            val valueKg = if (preferredUnit.lowercase() in listOf("lb", "lbs")) value / 2.20462 else value
            dataStore.saveWeight(valueKg, goalWeight.value)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Delegated actions and methods relocated to specialized ViewModels
    // ─────────────────────────────────────────────────────────────────

    // Training methods
    fun startWorkoutSession(planSession: PlanSession) {
        trainVM.startWorkoutSession(planSession)
        setTrainSubTab(1)
        selectTab(1)
    }

    fun selectDay(day: String) {
        trainVM.selectDay(day)
    }

    fun cancelActiveWorkout() {
        trainVM.cancelActiveWorkout()
    }

    fun logWorkoutSetState(exerciseId: String, setIndex: Int, weight: Double, reps: Int, rpe: Int, completed: Boolean, restTakenSeconds: Int = 0, repsInReserve: Int? = null) {
        trainVM.logWorkoutSetState(exerciseId, setIndex, weight, reps, rpe, completed, restTakenSeconds, repsInReserve)
    }

    fun openRirSelector(exerciseId: String, exerciseName: String, muscleGroup: String, setIndex: Int, weight: Double, reps: Int, totalSets: Int) {
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

    fun dismissSessionComplete() {
        trainVM.dismissSessionComplete()
    }

    // Weight and measurements methods delegated to HomeViewModel & ProgressViewModel
    fun logWeight(weight: Double, date: String = getTodayDateString(), preferredUnit: String = "kg") {
        homeVM.logWeight(weight, date, preferredUnit)
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

    fun deleteWeightById(id: Long) {
        homeVM.deleteWeightById(id)
    }

    fun exportUserData(context: android.content.Context, onComplete: (android.net.Uri?) -> Unit) {
        homeVM.exportUserData(context, onComplete)
    }

    // Database wipes CTA
    private val _isResetting = MutableStateFlow(false)
    val isResetting = _isResetting.asStateFlow()

    private val _resetError = MutableStateFlow<String?>(null)
    val resetError = _resetError.asStateFlow()

    fun resetAllData() {
        viewModelScope.launch {
            _isResetting.value = true
            _resetError.value = null
            trainVM.cancelActiveWorkout()
            try {
                withContext(Dispatchers.IO) {
                    db.clearAllTables()
                    dataStore.clearAllData()
                    com.apexfit.app.utils.SeedService.seed(getApplication())
                    trainVM.seedDefaultWorkoutPlan()
                    dataStore.setExercisesSeeded(true)
                }
            } catch (e: Exception) {
                android.util.Log.e("FitnessViewModel", "resetAllData seeding failed", e)
                _resetError.value = "Reset failed: ${e.message}. Please try again."
                // Do NOT call setExercisesSeeded(true) — allows re-seeding on next launch
            } finally {
                _isResetting.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        childStore.clear()
    }

    private fun getTodayDateString(): String {
        return com.apexfit.app.utils.DateTimeUtils.todayDateString()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                FitnessViewModel(app, this.createSavedStateHandle())
            }
        }
    }
}