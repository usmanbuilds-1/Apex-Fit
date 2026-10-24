@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.apexfit.app.data.*
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.ui.models.*
import com.apexfit.app.utils.AppConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class FitnessViewModel @Inject constructor(
    application: Application,
    private val repository: FitnessRepository,
    private val dataStore: DataStoreManager,
    private val dao: FitnessDao
) : AndroidViewModel(application) {

    private val db: AppDatabase get() = AppDatabase.getDatabase(getApplication())

    // User preferences & onboarding State (Expose from preferences)
    val isOnboarded: StateFlow<Boolean?> = dataStore.isOnboardedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val username = dataStore.usernameFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val goal = dataStore.goalFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Gain Muscle")
    val currentWeight = dataStore.currentWeightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.apexfit.app.UserDefaults.WEIGHT_KG)
    val goalWeight = dataStore.goalWeightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.apexfit.app.UserDefaults.WEIGHT_KG)
    val units = dataStore.unitsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "kg")
    
    val userHeight = dataStore.heightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.apexfit.app.UserDefaults.HEIGHT_CM)
    val userAge = dataStore.ageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.apexfit.app.UserDefaults.AGE_YEARS)
    val userSex = dataStore.sexFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "male")
    val weeklyWorkouts = dataStore.weeklyWorkoutsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    val userBioProfile: StateFlow<UserBioProfile> = combine(
        userHeight, userAge, userSex, weeklyWorkouts, goalWeight
    ) { h, a, s, w, gw -> UserBioProfile(h, a, s, w, gw) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserBioProfile.DEFAULT)

    val calorieTargetManual = dataStore.calorieTargetManualFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val calorieTargetValue = dataStore.calorieTargetValueFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.apexfit.app.UserDefaults.CALORIES)
    val equipmentAvailable = dataStore.equipmentFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Barbell,Dumbbell,Cable,Machine")

    // Train sub-tab state (0=PROGRAM, 1=WORKOUTS, 2=PLANS)
    private val _trainSubTab = MutableStateFlow(0)
    val trainSubTab: StateFlow<Int> = _trainSubTab.asStateFlow()

    fun setTrainSubTab(subTab: Int) {
        _trainSubTab.value = subTab
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
        val weightKg = if (isImperial) currentWeight / AppConstants.KG_TO_LBS else currentWeight
        val goalKg   = if (isImperial) goalWeight / AppConstants.KG_TO_LBS   else goalWeight
        val heightCm = if (isImperial) height * 2.54           else height
        val validWeight     = weightKg.coerceIn(AppConstants.MIN_WEIGHT_KG, AppConstants.MAX_WEIGHT_KG)
        val validGoalWeight = goalKg.coerceIn(AppConstants.MIN_WEIGHT_KG, AppConstants.MAX_WEIGHT_KG)
        val validHeight     = heightCm.coerceIn(AppConstants.MIN_HEIGHT_CM, AppConstants.MAX_HEIGHT_CM)
        val validAge       = age.coerceIn(AppConstants.MIN_AGE, AppConstants.MAX_AGE)
        val validGoal      = if (goal in listOf("Gain Muscle", "Lose Fat", "Maintain", "Recomposition")) goal else "Maintain"
        val validName      = username.trim().take(AppConstants.MAX_NAME_LENGTH).ifEmpty { "Athlete" }
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
        val validHeight    = height.coerceIn(AppConstants.MIN_HEIGHT_CM, AppConstants.MAX_HEIGHT_CM)
        val validAge       = age.coerceIn(AppConstants.MIN_AGE, AppConstants.MAX_AGE)
        val validGoal      = if (userGoal in listOf("Gain Muscle", "Lose Fat", "Maintain", "Recomposition")) userGoal else "Maintain"
        val validName      = name.trim().take(AppConstants.MAX_NAME_LENGTH).ifEmpty { "Athlete" }
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
            val valueKg = if (preferredUnit.lowercase() in listOf("lb", "lbs")) value / AppConstants.KG_TO_LBS else value
            dataStore.saveWeight(currentWeight.value, valueKg)
        }
    }

    fun setBodyWeight(value: Double, preferredUnit: String = "kg") {
        viewModelScope.launch {
            val valueKg = if (preferredUnit.lowercase() in listOf("lb", "lbs")) value / AppConstants.KG_TO_LBS else value
            dataStore.saveWeight(valueKg, goalWeight.value)
        }
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
            com.apexfit.app.utils.WorkoutForegroundService.stop(getApplication())
            com.apexfit.app.utils.RestTimerAlarmReceiver.cancelAlarm(getApplication())
            try {
                withContext(Dispatchers.IO) {
                    db.clearAllTables()
                    dataStore.clearAllData()
                    com.apexfit.app.utils.SeedService.seed(getApplication())
                    seedDefaultWorkoutPlan()
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

    private suspend fun seedDefaultWorkoutPlan() {
        val plan = WorkoutPlan(
            id = 0,
            name = "Scientific Hypertrophy Split",
            goal = "Gain Muscle",
            isActive = true,
            createdAt = System.currentTimeMillis()
        )

        dao.deleteAllPlans()
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
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Incline Barbell Bench Press", muscleGroup = "Chest", sets = 4, repsMin = 6, repsMax = 10, weight = 80.0, restSeconds = 120, notes = "Focus on the deep stretch at chest level."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Weighted Pull-Up", muscleGroup = "Back", sets = 4, repsMin = 6, repsMax = 10, weight = 5.0, restSeconds = 120, notes = "Control the eccentric descent."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Dumbbell Lateral Raise", muscleGroup = "Shoulders", sets = 3, repsMin = 10, repsMax = 15, weight = 12.5, restSeconds = 90, notes = "Slight torso lean forward."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Incline Dumbbell Bicep Curl", muscleGroup = "Biceps", sets = 3, repsMin = 8, repsMax = 12, weight = 14.0, restSeconds = 90, notes = "Biceps fully stretched at bottom."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Dual Rope Tricep Pushdown", muscleGroup = "Triceps", sets = 3, repsMin = 10, repsMax = 15, weight = 25.0, restSeconds = 90, notes = "Flare ropes outward at end of range."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Barbell Back Squat", muscleGroup = "Quads", sets = 4, repsMin = 6, repsMax = 8, weight = 100.0, restSeconds = 180, notes = "Keep knees tracking over toes."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Romanian Deadlift", muscleGroup = "Hamstrings", sets = 4, repsMin = 8, repsMax = 12, weight = 90.0, restSeconds = 120, notes = "Hinge at hips, keep back flat."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Leg Press (High & Wide)", muscleGroup = "Quads", sets = 3, repsMin = 10, repsMax = 12, weight = 160.0, restSeconds = 120, notes = "Aesthetic emphasis on quad sweep."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Seated Leg Curl", muscleGroup = "Hamstrings", sets = 3, repsMin = 10, repsMax = 15, weight = 50.0, restSeconds = 90, notes = "Hard squeeze at full flexion."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Standing Calf Raise", muscleGroup = "Calves", sets = 4, repsMin = 12, repsMax = 15, weight = 60.0, restSeconds = 60, notes = "2-second pause at full stretch."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Standing Overhead Press", muscleGroup = "Shoulders", sets = 4, repsMin = 6, repsMax = 10, weight = 50.0, restSeconds = 120, notes = "Press overhead in a straight line."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Chest-Supported Dumbbell Row", muscleGroup = "Back", sets = 4, repsMin = 8, repsMax = 12, weight = 25.0, restSeconds = 120, notes = "Squeeze shoulder blades together."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Flat Dumbbell Press", muscleGroup = "Chest", sets = 3, repsMin = 8, repsMax = 12, weight = 30.0, restSeconds = 120, notes = "Drive dumbbells toward center on press."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Lat Pulldown (Neutral Grip)", muscleGroup = "Back", sets = 3, repsMin = 8, repsMax = 12, weight = 65.0, restSeconds = 90, notes = "Pull down to upper collarbone."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Hammer Bicep Curl", muscleGroup = "Biceps", sets = 3, repsMin = 10, repsMax = 15, weight = 15.0, restSeconds = 90, notes = "Focus on brachialis and forearm development."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Conventional Deadlift", muscleGroup = "Back", sets = 3, repsMin = 5, repsMax = 5, weight = 120.0, restSeconds = 180, notes = "Full reset each rep, do not bounce."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Bulgarian Split Squat", muscleGroup = "Quads", sets = 3, repsMin = 8, repsMax = 12, weight = 16.0, restSeconds = 90, notes = "Load front heel, maintain vertical spine."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Leg Extension", muscleGroup = "Quads", sets = 3, repsMin = 10, repsMax = 15, weight = 60.0, restSeconds = 90, notes = "Peak contraction at top."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Lying Leg Curl", muscleGroup = "Hamstrings", sets = 3, repsMin = 10, repsMax = 12, weight = 40.0, restSeconds = 90, notes = "Keep hips flat against the pad."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Seated Calf Raise", muscleGroup = "Calves", sets = 4, repsMin = 12, repsMax = 15, weight = 40.0, restSeconds = 60, notes = "Slow stretch at bottom range.")
        )

        val resolvedExercises = exercises.map { exercise ->
            val exerciseId = dao.getExerciseByName(exercise.name)?.id
                ?: com.apexfit.app.utils.exerciseNameToSlug(exercise.name)
            exercise.copy(exerciseId = exerciseId)
        }

        dao.insertPlanExercises(resolvedExercises)
    }

    private fun getTodayDateString(): String {
        return com.apexfit.app.utils.DateTimeUtils.todayDateString()
    }
}

data class UserBioProfile(
    val height: Double,
    val age: Int,
    val sex: String,
    val weeklyWorkouts: Int,
    val goalWeight: Double
) {
    companion object {
        val DEFAULT = UserBioProfile(
            height = com.apexfit.app.UserDefaults.HEIGHT_CM,
            age = com.apexfit.app.UserDefaults.AGE_YEARS,
            sex = "male",
            weeklyWorkouts = 3,
            goalWeight = com.apexfit.app.UserDefaults.WEIGHT_KG
        )
    }
}