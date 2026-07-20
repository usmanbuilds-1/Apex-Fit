@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.data.repository.FitnessRepositoryImpl
import com.apexfit.app.data.WeightEntry
import com.apexfit.app.ui.models.*
import com.apexfit.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = com.apexfit.app.di.ServiceLocator.database(application)
    private val dao = db.fitnessDao()
    private val dataStore = com.apexfit.app.di.ServiceLocator.dataStore(application)
    private val repository = com.apexfit.app.di.ServiceLocator.repository(application)

    private val _isLoggingWeight = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _todayDate = MutableStateFlow(getTodayDateString())

    init {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val calendar = java.util.Calendar.getInstance()
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val midnight = calendar.timeInMillis
                val delayTime = (midnight - now).coerceAtLeast(0L)
                kotlinx.coroutines.delay(delayTime)
                _todayDate.value = getTodayDateString()
            }
        }
    }

    fun refreshTodayDate() {
        _todayDate.value = getTodayDateString()
    }

    // Raw database/preference flows
    private val weightFlow: Flow<List<com.apexfit.app.utils.WeightEntry>> = dao.getAllWeightEntriesFlow()
        .map { list -> list.map { com.apexfit.app.utils.WeightEntry(it.date, it.weight) } }
        .flowOn(Dispatchers.IO)

    private val nutritionFlow: Flow<List<com.apexfit.app.utils.NutritionEntry>> = dao.getAllNutritionEntriesFlow()
        .map { list ->
            list.map {
                com.apexfit.app.utils.NutritionEntry(
                    date = it.date,
                    calories = it.calories,
                    protein = it.protein.roundToInt(),
                    carbs = it.carbs.roundToInt(),
                    fat = it.fat.roundToInt()
                )
            }
        }
        .flowOn(Dispatchers.IO)

    val targetsFlow: Flow<com.apexfit.app.utils.NutritionTargets> = combine(
        dataStore.calorieTargetValueFlow,
        weightFlow,
        dataStore.goalFlow
    ) { calorieTarget, weights, goal ->
        val latestWeight = weights.lastOrNull()?.weight ?: com.apexfit.app.UserDefaults.WEIGHT_KG
        val proteinTarget = (latestWeight * com.apexfit.app.UserDefaults.PROTEIN_PER_KG).roundToInt().coerceIn(100, 250)
        val fatTarget = (calorieTarget * 0.25 / com.apexfit.app.utils.AppConstants.CALORIES_PER_GRAM_FAT).roundToInt().coerceIn(45, 120)
        val carbsTarget = ((calorieTarget - (proteinTarget * com.apexfit.app.utils.AppConstants.CALORIES_PER_GRAM_PROTEIN.toInt()) - (fatTarget * com.apexfit.app.utils.AppConstants.CALORIES_PER_GRAM_FAT.toInt())) / com.apexfit.app.utils.AppConstants.CALORIES_PER_GRAM_CARB).roundToInt().coerceIn(100, 500)
        
        com.apexfit.app.utils.NutritionTargets(
            calories = calorieTarget,
            protein = proteinTarget,
            carbs = carbsTarget,
            fat = fatTarget,
            weeklyTrainingSessions = 4
        )
    }.flowOn(Dispatchers.IO)

    val richSessionsFlow: Flow<List<com.apexfit.app.utils.TrainingSession>> =
        com.apexfit.app.di.ServiceLocator.richSessionsFlow

    // Weight logging State
    val weightHistory: StateFlow<UiState<List<UiWeightEntry>>> = repository.getWeightHistory()
        .map { entries -> UiState.Success(entries.map { it.toUi() }) as UiState<List<UiWeightEntry>> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val allNutritionHistory: StateFlow<List<UiNutritionEntry>> = dao.getAllNutritionEntriesFlow()
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayNutrition: StateFlow<List<UiNutritionEntry>> = _todayDate
        .flatMapLatest { date -> dao.getNutritionForDateFlow(date) }
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentWeight: Flow<Double> = dataStore.currentWeightFlow
    val calorieTargetFlow: Flow<Int> = dataStore.calorieTargetValueFlow

    val loggedCalories: StateFlow<Int> = todayNutrition.map { meals ->
        meals.sumOf { it.calories }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val loggedProtein: StateFlow<Int> = todayNutrition.map { meals ->
        meals.sumOf { it.protein }.roundToInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val loggedCarbs: StateFlow<Int> = todayNutrition.map { meals ->
        meals.sumOf { it.carbs }.roundToInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val loggedFat: StateFlow<Int> = todayNutrition.map { meals ->
        meals.sumOf { it.fat }.roundToInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val macroTargets: StateFlow<UiState<com.apexfit.app.utils.NutritionTargets>> = targetsFlow
        .map { UiState.Success(it) as UiState<com.apexfit.app.utils.NutritionTargets> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val todayExercisesFlow: Flow<List<com.apexfit.app.data.PlanExercise>> = repository.getActivePlan().flatMapLatest { plan ->
        val sessions = if (plan != null) dao.getSessionsForPlanFlow(plan.id) else flowOf(emptyList())
        sessions.flatMapLatest { sessionList ->
            val todayDayString = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
            val todaySession = sessionList.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
            if (todaySession != null) dao.getExercisesForSessionFlow(todaySession.id) else flowOf(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    // Readiness
    val sessionReadiness: StateFlow<UiSessionReadiness?> = combine(
        nutritionFlow,
        richSessionsFlow,
        targetsFlow,
        todayExercisesFlow
    ) { nutrition, sessions, targets, todayExercises ->
        val res = withContext(Dispatchers.Default) {
            if (sessions.isEmpty()) null
            else {
                val scoreResult = com.apexfit.app.utils.ReadinessFinal.buildReadinessInputs(
                    completedSessions = sessions,
                    todayExercises = todayExercises,
                    nutritionLog = nutrition,
                    targets = targets
                )
                
                val predictionText = "Systemic CNS readiness is ${scoreResult.systemicReadiness}%. " +
                        "Acute-to-chronic ratio modifier is ${String.format(java.util.Locale.US, "%.2f", scoreResult.acrModifier)}."

                val factorList = mutableListOf<com.apexfit.app.ui.models.UiReadinessFactor>()
                factorList.add(com.apexfit.app.ui.models.UiReadinessFactor(
                    name = "Systemic CNS",
                    impact = if (scoreResult.systemicReadiness >= 70) "positive" else if (scoreResult.systemicReadiness >= 50) "neutral" else "negative",
                    value = "${scoreResult.systemicReadiness}%"
                ))
                factorList.add(com.apexfit.app.ui.models.UiReadinessFactor(
                    name = "Nutrition",
                    impact = if (scoreResult.nutritionScore >= 70) "positive" else if (scoreResult.nutritionScore >= 50) "neutral" else "negative",
                    value = "${scoreResult.nutritionScore}%"
                ))
                scoreResult.muscleDetails.forEach { md ->
                    factorList.add(com.apexfit.app.ui.models.UiReadinessFactor(
                        name = "${md.muscleGroup.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }} Recovery",
                        impact = if (md.readinessPercent >= 70) "positive" else if (md.readinessPercent >= 50) "neutral" else "negative",
                        value = "${md.readinessPercent}% (${md.confidence})"
                    ))
                }

                com.apexfit.app.ui.models.UiSessionReadiness(
                    score = scoreResult.overallPercent,
                    label = scoreResult.label,
                    colorHex = scoreResult.colorHex,
                    prediction = predictionText,
                    recommendation = scoreResult.recommendation,
                    factors = factorList
                )
            }
        }
        res
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Compliance
    val complianceScores: StateFlow<UiComplianceResult> = combine(
        nutritionFlow, richSessionsFlow, targetsFlow
    ) { nutrition, sessions, targets ->
        val res = withContext(Dispatchers.Default) {
            com.apexfit.app.utils.AlgorithmEngine.calcComplianceScores(nutrition, sessions, targets)
        }
        res.toUi()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.apexfit.app.utils.ComplianceResult(calories = 0, protein = 0, training = 0, overall = 0, weakestDay = null).toUi()
    )

    val complianceScore: StateFlow<Int> = complianceScores
        .map { it.overall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 88)

    // Home Analytics (Streaks)
    val streakResult: StateFlow<com.apexfit.app.utils.StreakResult> = combine(
        nutritionFlow, richSessionsFlow, targetsFlow
    ) { nutrition, sessions, targets ->
        withContext(Dispatchers.Default) {
            val validTargets = targets
            com.apexfit.app.utils.AlgorithmEngine.calcStreaks(nutrition, sessions, validTargets)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.apexfit.app.utils.StreakResult(com.apexfit.app.utils.StreakInfo(0), com.apexfit.app.utils.StreakInfo(0))
    )

    fun logWeight(weight: Double, date: String = getTodayDateString()) {
        if (!_isLoggingWeight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                dao.insertWeightEntry(WeightEntry(date = date, time = getCurrentLocalTimeString(), weight = weight))
                dataStore.saveWeight(weight, dataStore.goalWeightFlow.first())
            } finally {
                _isLoggingWeight.set(false)
            }
        }
    }

    fun deleteWeight(date: String) {
        android.util.Log.w("HomeViewModel", "deleteWeight(date) is deprecated — use deleteWeightById(id)")
        // Do not call dao here. Call sites must migrate to deleteWeightById.
    }

    fun deleteWeightById(id: Long) {
        viewModelScope.launch {
            dao.deleteWeightEntryById(id)
        }
    }

    fun exportUserData(context: android.content.Context, onComplete: (android.net.Uri?) -> Unit) {  
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {  
            try {  
                val weights = dao.getAllWeightEntries()  
                val nutrition = dao.getAllNutritionEntriesFlow().first()  
                val sessions = dao.getAllTrainingSessions()  
  
                val exportData = buildString {  
                    appendLine("# Apex Fit Data Export — ${java.util.Date()}")  
                    appendLine()  
                    appendLine("## Weight History")  
                    appendLine("date,time,weight_kg")  
                    weights.forEach { appendLine("${it.date},${it.time},${it.weight}") }  
                    appendLine()  
                    appendLine("## Nutrition Log")  
                    appendLine("date,name,calories,protein_g,carbs_g,fat_g")  
                    nutrition.forEach { appendLine("${it.date},\"${it.name}\",${it.calories},${it.protein},${it.carbs},${it.fat}") }  
                    appendLine()  
                    appendLine("## Workout Sessions")  
                    appendLine("date,type,duration_min,feel")  
                    sessions.forEach { appendLine("${it.date},\"${it.sessionType}\",${it.durationMinutes},${it.sessionFeel}") }  
                }  
  
                val file = java.io.File(context.getExternalFilesDir(null),  
                    "apexfit_export_${System.currentTimeMillis()}.csv")  
                file.writeText(exportData)  
                val uri = androidx.core.content.FileProvider.getUriForFile(  
                    context, "${context.packageName}.fileprovider", file)  
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(uri) }  
            } catch (e: Exception) {  
                android.util.Log.e("Export", "Export failed", e)  
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(null) }  
            }  
        }  
    }

    private fun getTodayDateString(): String {
        return com.apexfit.app.utils.DateTimeUtils.todayDateString()
    }

    private fun getCurrentLocalTimeString(): String {
        return android.text.format.DateFormat.getTimeFormat(getApplication()).format(java.util.Date())
    }

    public override fun onCleared() {
        super.onCleared()
    }
}