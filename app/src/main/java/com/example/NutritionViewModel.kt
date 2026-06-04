@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.repository.FitnessRepository
import com.example.data.repository.FitnessRepositoryImpl
import com.example.ui.models.*
import com.example.utils.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NutritionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.fitnessDao()
    private val dataStore = DataStoreManager(application)
    private val repository: FitnessRepository = FitnessRepositoryImpl(dao, dataStore)

    // Onboarding preferences for TDEE calculations
    val userGoal: StateFlow<String> = dataStore.goalFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Gain Muscle")
    val userHeight: StateFlow<Double> = dataStore.heightFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 175.0)
    val userAge: StateFlow<Int> = dataStore.ageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 25)
    val userSex: StateFlow<String> = dataStore.sexFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "male")

    private val _selectedNutritionDate = MutableStateFlow(getTodayDateString())
    val selectedNutritionDate: StateFlow<String> = _selectedNutritionDate.asStateFlow()

    val loggedMeals: StateFlow<List<UiNutritionEntry>> = _selectedNutritionDate.flatMapLatest { date ->
        repository.getNutritionEntries(date).map { list -> list.map { it.toUi() } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNutritionHistory: StateFlow<List<UiNutritionEntry>> = dao.getAllNutritionEntriesFlow()
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weightHistory: StateFlow<List<UiWeightEntry>> = repository.getWeightHistory()
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Caloric target values
    val calorieTargetManual: StateFlow<Boolean> = dataStore.calorieTargetManualFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val calorieTargetValue: StateFlow<Int> = dataStore.calorieTargetValueFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2500)

    val activeTdee: StateFlow<Int> = combine(
        weightHistory,
        allNutritionHistory,
        userHeight,
        userAge,
        userSex
    ) { weights, nutrition, height, age, sex ->
        val weightData = weights.map { it.toData() }
        val nutritionData = nutrition.map { it.toData() }
        
        val sexOffset = if (sex.equals("female", ignoreCase = true)) -161.0 else 5.0
        val latestWeight = weightData.firstOrNull()?.weight ?: 80.0
        val bmrBaseline = (10.0 * latestWeight) + (6.25 * height) - (5.0 * age) + sexOffset
        val fallbackTdee = (bmrBaseline * 1.55).toInt()
        
        val tdeeResult = com.example.utils.AlgorithmEngine.calcAdaptiveTDEE(
            weightLog = weightData,
            nutritionLog = nutritionData,
            windowDays = 14,
            heightCm = height,
            ageYears = age,
            biologicalSex = sex,
            weeklyWorkouts = 4
        )
        tdeeResult.tdee ?: fallbackTdee
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2500)

    val suggestedCaloricTarget: StateFlow<Int> = combine(
        activeTdee,
        userGoal
    ) { tdee, goal ->
        if (tdee > 0 && goal.isNotEmpty()) {
            com.example.utils.AlgorithmEngine.suggestCaloricTarget(tdee, goal)
        } else {
            2500
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2500)

    // Log meal and delete meal
    fun logNutrition(
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        date: String = getTodayDateString(),
        name: String = "Logged Meal"
    ) {
        viewModelScope.launch {
            val newEntry = com.example.data.NutritionEntry(
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

    // Water tracking - keeping client-side state for responsiveness
    private val _waterIntakeToday = MutableStateFlow(0) // in ml
    val waterIntakeToday: StateFlow<Int> = _waterIntakeToday.asStateFlow()

    fun logWater(ml: Int) {
        _waterIntakeToday.value = (_waterIntakeToday.value + ml).coerceAtLeast(0)
    }

    fun resetWater() {
        _waterIntakeToday.value = 0
    }

    private val geminiService = com.example.network.GeminiService(dataStore, repository)

    fun analyseMealPhotoGemini(
        bitmapBytes: ByteArray,
        onComplete: (String, Int, Double, Double, Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val base64 = android.util.Base64.encodeToString(bitmapBytes, android.util.Base64.NO_WRAP)
                val result = geminiService.parseMealPhoto(base64)
                if (result.isSuccess) {
                    val estimate = result.getOrThrow()
                    onComplete(
                        "AI Scanned Meal",
                        estimate.calories,
                        estimate.protein,
                        estimate.carbs,
                        estimate.fat
                    )
                } else {
                    onFailure(result.exceptionOrNull()?.message ?: "Unknown AI analysis failure")
                }
            } catch (e: Exception) {
                onFailure(e.message ?: "Unknown error")
            }
        }
    }

    fun getDateDaysAgo(daysAgo: Int): String {
        return com.example.utils.AlgorithmEngine.getDateDaysAgo(daysAgo)
    }

    private fun getTodayDateString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }

    private fun getCurrentLocalTimeString(): String {
        return java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date())
    }
}
