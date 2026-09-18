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
import kotlinx.coroutines.cancel
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
    val todayDate: StateFlow<String> = _todayDate.asStateFlow()

    private val _weightLogError = MutableStateFlow<String?>(null)
    val weightLogError: StateFlow<String?> = _weightLogError.asStateFlow()

    fun refreshTodayDate() {
        _todayDate.value = getTodayDateString()
    }

    // Raw database/preference flows
    private val weightFlow = com.apexfit.app.di.ServiceLocator.weightEntriesFlow

    private val nutritionFlow: Flow<List<com.apexfit.app.utils.NutritionEntry>> = dao.getNutritionEntriesSince(getDateDaysAgo(30))
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

    val targetsFlow: Flow<com.apexfit.app.data.NutritionTargets> =
        com.apexfit.app.di.ServiceLocator.sharedTargetsFlow

    val richSessionsFlow: Flow<List<com.apexfit.app.utils.TrainingSession>> =
        com.apexfit.app.di.ServiceLocator.richSessionsFlow

    // Weight logging State
    val weightHistory: StateFlow<UiState<List<UiWeightEntry>>> = repository.getWeightHistory()
        .map { entries -> UiState.Success(entries.map { it.toUi() }) as UiState<List<UiWeightEntry>> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val allNutritionHistory: StateFlow<List<UiNutritionEntry>> =
        com.apexfit.app.di.ServiceLocator.nutritionEntriesFlow
            .map { list -> list.map { it.toUi() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayNutrition: StateFlow<List<UiNutritionEntry>> = _todayDate
        .flatMapLatest { date -> dao.getNutritionForDateFlow(date) }
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentWeight: Flow<Double> = dataStore.currentWeightFlow
    val calorieTargetFlow: Flow<Int> = dataStore.calorieTargetValueFlow

    val loggedCalories: StateFlow<Int> = todayNutrition.map { meals ->
        meals.sumOf { it.calories }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val loggedProtein: StateFlow<Int> = todayNutrition.map { meals ->
        meals.sumOf { it.protein }.roundToInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val loggedCarbs: StateFlow<Int> = todayNutrition.map { meals ->
        meals.sumOf { it.carbs }.roundToInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val loggedFat: StateFlow<Int> = todayNutrition.map { meals ->
        meals.sumOf { it.fat }.roundToInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val macroTargets: StateFlow<UiState<com.apexfit.app.utils.NutritionTargets>> = targetsFlow
        .map { UiState.Success(it) as UiState<com.apexfit.app.utils.NutritionTargets> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val todayExercisesFlow: Flow<List<com.apexfit.app.data.PlanExercise>> = repository.getActivePlan().flatMapLatest { plan ->
        val sessions = if (plan != null) dao.getSessionsForPlanFlow(plan.id) else flowOf(emptyList())
        sessions.flatMapLatest { sessionList ->
            val todayDayString = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
            val todaySession = sessionList.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
            if (todaySession != null) dao.getExercisesForSessionFlow(todaySession.id) else flowOf(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    // Readiness
    val sessionReadiness: StateFlow<UiSessionReadiness?> =
        com.apexfit.app.di.ServiceLocator.sessionReadinessFlow

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
        SharingStarted.WhileSubscribed(5_000),
        com.apexfit.app.utils.ComplianceResult(calories = 0, protein = 0, training = 0, overall = 0, weakestDay = null).toUi()
    )

    val complianceScore: StateFlow<Int> = complianceScores
        .map { it.overall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
        SharingStarted.WhileSubscribed(5_000),
        com.apexfit.app.utils.StreakResult(com.apexfit.app.utils.StreakInfo(0), com.apexfit.app.utils.StreakInfo(0))
    )

    fun logWeight(weight: Double, date: String = getTodayDateString(), preferredUnit: String = "kg"): kotlinx.coroutines.Job? {
        if (!_isLoggingWeight.compareAndSet(false, true)) return null
        val weightKg = if (preferredUnit.lowercase() in listOf("lb", "lbs")) weight / 2.20462 else weight
        val safeWeightKg = weightKg.coerceIn(
            com.apexfit.app.utils.AppConstants.MIN_WEIGHT_KG,
            com.apexfit.app.utils.AppConstants.MAX_WEIGHT_KG
        )
        return viewModelScope.launch {
            try {
                _weightLogError.value = null
                val timeStr = try { getCurrentLocalTimeString() } catch (e: Exception) { "12:00" }
                dao.insertWeightEntry(WeightEntry(date = date, time = timeStr, weight = safeWeightKg, unit = "kg"))
                val goal = try {
                    kotlinx.coroutines.withTimeoutOrNull(500) { dataStore.goalWeightFlow.first() }
                } catch (e: Exception) {
                    null
                }
                if (goal != null) {
                    try {
                        dataStore.saveWeight(safeWeightKg, goal)
                    } catch (e: Exception) { }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _weightLogError.value = "Failed to save weight. Please try again."
            } finally {
                _isLoggingWeight.set(false)
            }
        }
    }

    fun deleteWeightById(id: Long) {
        viewModelScope.launch {
            dao.deleteWeightEntryById(id)
        }
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') ||
                   escaped.contains('\n') || escaped.contains('\r')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    fun exportUserData(context: android.content.Context, onComplete: (android.net.Uri?) -> Unit) {  
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {  
            try {  
                val weights = dao.getAllWeightEntries()  
                val nutrition = dao.getAllNutritionEntriesFlow().first()  
                val sessions = dao.getAllTrainingSessions()  
                val exerciseSets = dao.getAllExerciseSets()
                val personalRecords = dao.getAllPRsFlow().first()
                val bodyMeasurements = dao.getAllBodyMeasurementsFlow().first()
  
                val exportData = buildString {  
                    appendLine("# Apex Fit Data Export — ${java.util.Date()}")  
                    appendLine()  
                    appendLine("## Weight History")  
                    appendLine("date,time,weight_kg")  
                    weights.forEach { appendLine("${it.date},${it.time},${it.weight}") }  
                    appendLine()  
                    appendLine("## Nutrition Log")  
                    appendLine("date,name,calories,protein_g,carbs_g,fat_g")  
                    nutrition.forEach { appendLine("${it.date},${csvEscape(it.name)},${it.calories},${it.protein},${it.carbs},${it.fat}") }  
                    appendLine()  
                    appendLine("## Workout Sessions")  
                    appendLine("date,type,duration_min,feel")  
                    sessions.forEach { appendLine("${it.date},${csvEscape(it.sessionType)},${it.durationMinutes},${it.sessionFeel}") }  
                    appendLine()  
                    appendLine("## Exercise Sets")  
                    appendLine("sessionId,exerciseId,exerciseName,muscleGroup,weight,reps,rpe,isWarmup,restTaken,completed,repsInReserve,effectiveSetValue,weight_unit")  
                    exerciseSets.forEach { appendLine("${csvEscape(it.sessionId)},${csvEscape(it.exerciseId)},${csvEscape(it.exerciseName)},${csvEscape(it.muscleGroup)},${it.weight},${it.reps},${it.rpe},${it.isWarmup},${it.restTaken},${it.completed},${it.repsInReserve},${it.effectiveSetValue},${csvEscape(it.weightUnit)}") }  
                    appendLine()  
                    appendLine("## Personal Records")  
                    appendLine("id,exerciseId,type,value,date")  
                    personalRecords.forEach { appendLine("${csvEscape(it.id)},${csvEscape(it.exerciseId)},${csvEscape(it.type)},${it.value},${csvEscape(it.date)}") }  
                    appendLine()  
                    appendLine("## Body Measurements")  
                    appendLine("date,bodyPart,value,unit")  
                    bodyMeasurements.forEach { appendLine("${csvEscape(it.date)},${csvEscape(it.bodyPart)},${it.value},${csvEscape(it.unit)}") }  
                }  
  
                val file = java.io.File(context.getExternalFilesDir(null), "apexfit_export.csv")  
                file.writeText(exportData)  
                val uri = androidx.core.content.FileProvider.getUriForFile(  
                    context, "${context.packageName}.fileprovider", file)  
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(uri) }  
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    kotlinx.coroutines.delay(60_000)
                    file.delete()
                }
            } catch (e: Exception) {  
                android.util.Log.e("Export", "Export failed", e)  
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(null) }  
            }  
        }  
    }

    private fun getTodayDateString(): String {
        return com.apexfit.app.utils.DateTimeUtils.todayDateString()
    }

    private fun getCurrentLocalTimeString(): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())

    public override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }

    fun clearWeightLogError() { _weightLogError.value = null }
}