@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.data.WeightEntry
import com.apexfit.app.ui.models.*
import com.apexfit.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.apexfit.app.data.FitnessDao

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val repository: FitnessRepository,
    private val dataStore: DataStoreManager,
    private val dao: FitnessDao
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = com.apexfit.app.data.repository.FitnessRepositoryImpl(
            com.apexfit.app.data.AppDatabase.getDatabase(application),
            com.apexfit.app.data.AppDatabase.getDatabase(application).fitnessDao(),
            com.apexfit.app.data.DataStoreManager.getInstance(application)
        ),
        dataStore = com.apexfit.app.data.DataStoreManager.getInstance(application),
        dao = com.apexfit.app.data.AppDatabase.getDatabase(application).fitnessDao()
    )

    private val _isLoggingWeight = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _todayDate = MutableStateFlow(java.time.LocalDate.now().toString())
    val todayDate: StateFlow<String> = _todayDate.asStateFlow()

    init {
        // This is a wall-clock timer, not virtual business logic. Keep its delay off
        // Dispatchers.Main so runTest's advanceUntilIdle cannot loop through days.
        viewModelScope.launch(Dispatchers.Default) {
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
    private val weightFlow = repository.weightEntriesFlow

    val targetsFlow: Flow<com.apexfit.app.data.NutritionTargets> =
        repository.sharedTargetsFlow

    val richSessionsFlow: Flow<List<com.apexfit.app.utils.TrainingSession>> =
        repository.richSessionsFlow

    // Weight logging State
    val weightHistory: StateFlow<UiState<List<UiWeightEntry>>> = repository.getWeightHistory()
        .map { entries -> UiState.Success(entries.map { it.toUi() }) as UiState<List<UiWeightEntry>> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val allNutritionHistory: StateFlow<List<UiNutritionEntry>> =
        repository.nutritionEntriesFlow
            .map { list -> list.map { it.toUi() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayNutrition: StateFlow<List<UiNutritionEntry>> = _todayDate
        .flatMapLatest { date -> dao.getNutritionForDateFlow(date) }
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentWeight: Flow<Double> = repository.getCurrentWeightFlow()
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

    val todayWorkoutChoice = repository.todayWorkoutChoiceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayExercisesFlow: Flow<List<com.apexfit.app.data.PlanExercise>> =
        repository.todayExercisesFlow

    private val exerciseLibraryFlow = dao.getAllExercisesFlow()

    val todayHeroCategory: StateFlow<SessionHeroCategory> = combine(
        todayExercisesFlow,
        exerciseLibraryFlow
    ) { plannedExercises, library ->
        val byId = library.associateBy { it.id }
        val heroExercises = plannedExercises.map { planned ->
            val metadata = byId[planned.exerciseId]
            SessionHeroExercise(
                primaryMuscle = metadata?.primaryMuscle ?: planned.muscleGroup,
                secondaryMuscles = metadata?.secondaryMuscles.orEmpty(),
                workingSets = planned.sets.coerceAtLeast(0)
            )
        }
        SessionHeroResolver.resolve(heroExercises)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SessionHeroCategory.RECOVERY
    )

    val units: StateFlow<String> = dataStore.unitsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "kg")

    val completedSessions: Flow<List<com.apexfit.app.data.RichTrainingSession>> = richSessionsFlow

    val muscleTargetHistory: Flow<List<MuscleTargetRevision>> = dataStore.muscleTargetHistoryFlow

    suspend fun scheduleMuscleTarget(muscle: String, target: Int?, expectedWeek: java.time.LocalDate) =
        dataStore.scheduleMuscleTarget(muscle, target, expectedWeek)

    fun observeMuscleHistory(start: java.time.LocalDate, end: java.time.LocalDate):
        Flow<List<com.apexfit.app.data.RichTrainingSession>> =
        dao.observeAnalyticsWorkingSets(start.toString(), end.toString())
            .map(AnalyticsHistory::sessions)
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    val currentWeekMuscleHeatmap: StateFlow<Map<String, HeatmapEntry>> = todayDate
        .flatMapLatest { date ->
            val today = java.time.LocalDate.parse(date)
            val start = MuscleVolumePeriod.WEEK.window(today).start
            observeMuscleHistory(start, today).map {
                FatigueEngine.directHeatmap(MuscleVolumeSummary.calculateWindow(it, start, today))
            }
        }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())


    val exercisesForPlan: Flow<List<com.apexfit.app.data.PlanExercise>> = dao.getAllPlanExercisesFlow()

    val estimatedWorkoutDuration: StateFlow<Int> = combine(
        todayExercisesFlow, units
    ) { exercises, units ->
        com.apexfit.app.utils.WorkoutDurationEstimate.estimate(exercises).minutes
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
        repository.sessionReadinessFlow

    // Compliance
    val complianceScores: StateFlow<UiComplianceResult> =
        repository.complianceScoresFlow

    val complianceScore: StateFlow<Int> = complianceScores
        .map { it.overall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // Home Analytics (Streaks)
    val streakResult: StateFlow<com.apexfit.app.utils.StreakResult> =
        repository.streakResultFlow

    private fun canonicalWeight(weight: Double, preferredUnit: String): Double {
        val kg = when (preferredUnit.lowercase()) {
            "kg" -> weight
            "lb", "lbs" -> weight / AppConstants.KG_TO_LBS
            else -> throw IllegalArgumentException("Unknown weight unit")
        }
        require(kg.isFinite() && kg in AppConstants.MIN_WEIGHT_KG..AppConstants.MAX_WEIGHT_KG)
        return kg
    }

    private fun saveWeightAction(action: suspend () -> Unit): kotlinx.coroutines.Job? {
        if (!_isLoggingWeight.compareAndSet(false, true)) return null
        return viewModelScope.launch {
            try {
                _weightLogError.value = null
                action()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _weightLogError.value = "Enter a valid weight and date."
            } catch (e: Exception) {
                _weightLogError.value = "Failed to save weight. Please try again."
            } finally {
                _isLoggingWeight.set(false)
            }
        }
    }

    fun logWeight(weight: Double, date: String? = null, preferredUnit: String = "kg"): kotlinx.coroutines.Job? {
        val now = java.time.ZonedDateTime.now()
        return saveWeightAction {
            val localDate = date ?: now.toLocalDate().toString()
            require(!java.time.LocalDate.parse(localDate).isAfter(now.toLocalDate()))
            val time = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS", java.util.Locale.US))
            repository.logWeightEntry(WeightEntry(date = localDate, time = time, weight = canonicalWeight(weight, preferredUnit), unit = "kg"))
        }
    }

    fun editWeightById(id: Long, weight: Double, preferredUnit: String = "kg"): kotlinx.coroutines.Job? =
        saveWeightAction {
            check(repository.editWeightEntry(id, canonicalWeight(weight, preferredUnit))) { "Weight no longer exists" }
        }

    fun deleteWeightById(id: Long): kotlinx.coroutines.Job =
        viewModelScope.launch {
            try {
                _weightLogError.value = null
                repository.removeWeightEntry(id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _weightLogError.value = "Failed to delete weight. Please try again."
            }
        }


    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    fun exportUserData(destination: android.net.Uri, onComplete: (Boolean) -> Unit) {
        if (!_isExporting.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.apexfit.app.utils.SecureUserExport.save(getApplication<Application>(), destination)
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onComplete(false) }
            } finally {
                _isExporting.value = false
            }
        }
    }

    private fun getTodayDateString(): String {
        return com.apexfit.app.utils.DateTimeUtils.todayDateString()
    }


    public override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }

    fun clearWeightLogError() { _weightLogError.value = null }
}
