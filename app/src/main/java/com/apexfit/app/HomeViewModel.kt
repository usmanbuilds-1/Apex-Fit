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

    private val _todayDate = MutableStateFlow(java.time.LocalDate.now().toString())
    val todayDate: StateFlow<String> = _todayDate.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val tomorrow = java.time.LocalDate.now().plusDays(1).atStartOfDay()
                val millis = java.time.Duration.between(java.time.LocalDateTime.now(), tomorrow).toMillis()
                kotlinx.coroutines.delay(millis + 1000)
                _todayDate.value = java.time.LocalDate.now().toString()
            }
        }
    }

    private val _weightLogError = MutableStateFlow<String?>(null)
    val weightLogError: StateFlow<String?> = _weightLogError.asStateFlow()

    fun refreshTodayDate() {
        _todayDate.value = getTodayDateString()
    }

    // Raw database/preference flows
    private val weightFlow = com.apexfit.app.di.ServiceLocator.weightEntriesFlow

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

    data class TodayMacros(val calories: Int, val protein: Int, val carbs: Int, val fat: Int)

    private val todayMacros: StateFlow<TodayMacros> = todayNutrition.map { meals ->
        TodayMacros(
            calories = meals.sumOf { it.calories },
            protein  = meals.sumOf { it.protein }.roundToInt(),
            carbs    = meals.sumOf { it.carbs }.roundToInt(),
            fat      = meals.sumOf { it.fat }.roundToInt()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayMacros(0, 0, 0, 0))

    val loggedCalories: StateFlow<Int> = todayMacros.map { it.calories }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val loggedProtein:  StateFlow<Int> = todayMacros.map { it.protein }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val loggedCarbs:    StateFlow<Int> = todayMacros.map { it.carbs }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val loggedFat:      StateFlow<Int> = todayMacros.map { it.fat }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val macroTargets: StateFlow<UiState<com.apexfit.app.utils.NutritionTargets>> = targetsFlow
        .map { UiState.Success(it) as UiState<com.apexfit.app.utils.NutritionTargets> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val todayExercisesFlow: Flow<List<com.apexfit.app.data.PlanExercise>> =
        com.apexfit.app.di.ServiceLocator.todayExercisesFlow

    val units: StateFlow<String> = dataStore.unitsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "kg")

    val completedSessions: Flow<List<com.apexfit.app.data.RichTrainingSession>> = richSessionsFlow

    val exercisesForPlan: Flow<List<com.apexfit.app.data.PlanExercise>> = dao.getAllPlanExercisesFlow()

    val estimatedWorkoutDuration: StateFlow<Int> = combine(
        todayExercisesFlow, units
    ) { exercises, units ->
        exercises.sumOf { ex -> AlgorithmEngine.estimateSetDurationMinutes(ex, units) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val lastTrainedDateMap: StateFlow<Map<String, String>> = combine(
        completedSessions, exercisesForPlan
    ) { sessions, exercises ->
        // Move the nested-loop + 15-branch when() string matching logic here
        buildLastTrainedMap(sessions, exercises)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun buildLastTrainedMap(
        sessions: List<com.apexfit.app.data.RichTrainingSession>,
        exercises: List<com.apexfit.app.data.PlanExercise>
    ): Map<String, String> {
        val exerciseMuscleMap = exercises.associate { it.name.lowercase().trim() to it.muscleGroup }
        val mapping = mutableMapOf<String, String>()
        val sortedSessions = sessions.sortedByDescending { it.date }
        for (session in sortedSessions) {
            val dateStr = session.date
            for (exercise in session.exercises) {
                val rawMuscle = exercise.muscleGroup.ifBlank {
                    exerciseMuscleMap[exercise.name.lowercase().trim()] ?: ""
                }
                val exerciseMuscle = rawMuscle.lowercase().trim()
                val matchedCanvasMuscle = when {
                    exerciseMuscle.contains("chest") || exerciseMuscle.contains("pectoral") -> "chest"
                    exerciseMuscle.contains("back") && !exerciseMuscle.contains("lower") -> "back"
                    exerciseMuscle.contains("front delt") || exerciseMuscle.contains("front_delt") || exerciseMuscle.contains("anterior delt") -> "front_delt"
                    exerciseMuscle.contains("rear delt") || exerciseMuscle.contains("rear_delt") || exerciseMuscle.contains("posterior delt") -> "rear_delt"
                    exerciseMuscle.contains("side delt") || exerciseMuscle.contains("side_delt") || exerciseMuscle.contains("lateral") || exerciseMuscle.contains("shoulder") || exerciseMuscle.contains("delt") -> "side_delt"
                    exerciseMuscle.contains("bicep") -> "bicep"
                    exerciseMuscle.contains("tricep") -> "tricep"
                    exerciseMuscle.contains("quad") || exerciseMuscle.contains("thigh") -> "quad"
                    exerciseMuscle.contains("hamstring") -> "hamstring"
                    exerciseMuscle.contains("glute") -> "glute"
                    exerciseMuscle.contains("calf") || exerciseMuscle.contains("calves") -> "calf"
                    exerciseMuscle.contains("core") || exerciseMuscle.contains("abs") || exerciseMuscle.contains("abdom") -> "core"
                    else -> null
                }
                if (matchedCanvasMuscle != null && !mapping.containsKey(matchedCanvasMuscle)) {
                    mapping[matchedCanvasMuscle] = dateStr
                }
            }
        }
        return mapping
    }

    // Readiness
    val sessionReadiness: StateFlow<UiSessionReadiness?> =
        com.apexfit.app.di.ServiceLocator.sessionReadinessFlow

    // Compliance
    val complianceScores: StateFlow<UiComplianceResult> =
        com.apexfit.app.di.ServiceLocator.complianceScoresFlow

    val complianceScore: StateFlow<Int> = complianceScores
        .map { it.overall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // Home Analytics (Streaks)
    val streakResult: StateFlow<com.apexfit.app.utils.StreakResult> =
        com.apexfit.app.di.ServiceLocator.streakResultFlow

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

                val file = java.io.File(context.getExternalFilesDir(null), "apexfit_export.csv")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    file.bufferedWriter().use { writer ->
                        writer.appendLine("# Apex Fit Data Export — ${java.util.Date()}")
                        writer.appendLine()
                        writer.appendLine("## Weight History")
                        writer.appendLine("date,time,weight_kg")
                        weights.forEach { writer.appendLine("${it.date},${it.time},${it.weight}") }
                        writer.appendLine()
                        writer.appendLine("## Nutrition Log")
                        writer.appendLine("date,name,calories,protein_g,carbs_g,fat_g")
                        nutrition.forEach { writer.appendLine("${it.date},${csvEscape(it.name)},${it.calories},${it.protein},${it.carbs},${it.fat}") }
                        writer.appendLine()
                        writer.appendLine("## Workout Sessions")
                        writer.appendLine("date,type,duration_min,feel")
                        sessions.forEach { writer.appendLine("${it.date},${csvEscape(it.sessionType)},${it.durationMinutes},${it.sessionFeel}") }
                        writer.appendLine()
                        writer.appendLine("## Exercise Sets")
                        writer.appendLine("sessionId,exerciseId,exerciseName,muscleGroup,weight,reps,rpe,isWarmup,restTaken,completed,repsInReserve,effectiveSetValue,weight_unit")
                        exerciseSets.forEach { writer.appendLine("${csvEscape(it.sessionId)},${csvEscape(it.exerciseId)},${csvEscape(it.exerciseName)},${csvEscape(it.muscleGroup)},${it.weight},${it.reps},${it.rpe},${it.isWarmup},${it.restTaken},${it.completed},${it.repsInReserve},${it.effectiveSetValue},${csvEscape(it.weightUnit)}") }
                        writer.appendLine()
                        writer.appendLine("## Personal Records")
                        writer.appendLine("id,exerciseId,type,value,date")
                        personalRecords.forEach { writer.appendLine("${csvEscape(it.id)},${csvEscape(it.exerciseId)},${csvEscape(it.type)},${it.value},${csvEscape(it.date)}") }
                        writer.appendLine()
                        writer.appendLine("## Body Measurements")
                        writer.appendLine("date,bodyPart,value,unit")
                        bodyMeasurements.forEach { writer.appendLine("${csvEscape(it.date)},${csvEscape(it.bodyPart)},${it.value},${csvEscape(it.unit)}") }
                    }
                }
   
                val uri = androidx.core.content.FileProvider.getUriForFile(  
                    context, "${context.packageName}.fileprovider", file)  
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(uri) }  
                kotlinx.coroutines.delay(60_000)
                file.delete()
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
        java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    public override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }

    fun clearWeightLogError() { _weightLogError.value = null }
}