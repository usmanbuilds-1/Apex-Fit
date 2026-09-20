package com.apexfit.app.di

import android.content.Context
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.data.repository.FitnessRepositoryImpl
import com.apexfit.app.domain.repository.FitnessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.data.NutritionTargets
import com.apexfit.app.data.WeightEntry
import com.apexfit.app.utils.SessionMapper
import com.apexfit.app.utils.getDateDaysAgo
import com.apexfit.app.ui.models.UiSessionReadiness
import com.apexfit.app.ui.models.toUi
import kotlin.math.roundToInt
import com.apexfit.app.utils.AlgorithmEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flatMapLatest
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.WorkoutPlan
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.TrainingSession
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.ui.models.UiComplianceResult
import com.apexfit.app.utils.StreakResult
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers

object ServiceLocator {
    @Volatile private var database: AppDatabase? = null
    @Volatile private var dataStore: DataStoreManager? = null
    @Volatile private var repository: FitnessRepository? = null

    @Volatile private var _appScope: CoroutineScope? = null
    @Volatile private var _appContext: Context? = null

    fun setAppScope(scope: CoroutineScope, context: Context) {
        _appScope = scope
        _appContext = context.applicationContext
    }

    val appScope get() = _appScope
        ?: error("appScope not set — call ServiceLocator.setAppScope() in Application.onCreate()")

    private val appContext get() = _appContext
        ?: error("appContext not set — call ServiceLocator.setAppScope() in Application.onCreate()")

    private val DAY_OF_WEEK_FORMAT = java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.US)

    private val bioProfileFlow: StateFlow<BioProfile> by lazy {
        val ds = dataStore(appContext)
        combine(
            ds.heightFlow,
            ds.ageFlow,
            ds.sexFlow,
            ds.weeklyWorkoutsFlow,
            ds.goalWeightFlow
        ) { height, age, sex, weekly, goalWeight ->
            BioProfile(height, age, sex, weekly, goalWeight)
        }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), BioProfile.DEFAULT)
    }

    val recent90DaySessionsFlow: StateFlow<List<TrainingSession>> by lazy {
        val dao = database(appContext).fitnessDao()
        dao.getRecentCompletedSessionsFlow(getDateDaysAgo(90))
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
    }

    val recent90DaySetsFlow: StateFlow<List<ExerciseSet>> by lazy {
        val dao = database(appContext).fitnessDao()
        dao.getRecentExerciseSetsFlow(getDateDaysAgo(90))
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
    }

    val richSessionsFlow: StateFlow<List<RichTrainingSession>> by lazy {
        combine(
            recent90DaySessionsFlow,
            recent90DaySetsFlow
        ) { sessions, sets ->
            SessionMapper.buildRichSessions(sessions, sets)
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    val weightEntriesFlow: StateFlow<List<com.apexfit.app.utils.WeightEntry>> by lazy {
        val dao = database(appContext).fitnessDao()
        dao.getWeightEntriesSinceFlow(com.apexfit.app.utils.getDateDaysAgo(90))
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    val nutritionEntriesFlow: StateFlow<List<com.apexfit.app.utils.NutritionEntry>> by lazy {
        val dao = database(appContext).fitnessDao()
        dao.getNutritionEntriesSinceFlow(
            com.apexfit.app.utils.getDateDaysAgo(90)
        )
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    val sessionReadinessFlow: StateFlow<UiSessionReadiness?> by lazy {
        val dao = database(appContext).fitnessDao()
        combine(
            richSessionsFlow,
            dataStore(appContext).calorieTargetValueFlow,
            dataStore(appContext).currentWeightFlow,
            nutritionEntriesFlow
        ) { sessions, calorieTarget, weight, nutritionList ->
            try {
                if (sessions.isEmpty()) {
                    null
                } else {
                    val mappedNutrition = nutritionList.map {
                        com.apexfit.app.utils.NutritionEntry(
                            date = it.date,
                            calories = it.calories,
                            protein = it.protein.roundToInt(),
                            carbs = it.carbs.roundToInt(),
                            fat = it.fat.roundToInt()
                        )
                    }
                    val scoreResult = com.apexfit.app.utils.ReadinessFinal.buildReadinessInputs(
                        sessions = sessions,
                        nutritionLog = mappedNutrition,
                        bodyWeightKg = weight,
                        calorieTarget = calorieTarget
                    )
                    scoreResult.toUi()
                }
            } catch (e: Exception) {
                android.util.Log.e("ServiceLocator", "Readiness scoring failed", e)
                null
            }
        }
        .debounce(300L)
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    }

    val sharedTargetsFlow: Flow<NutritionTargets> by lazy {
        val dao = database(appContext).fitnessDao()
        val ds = dataStore(appContext)
        val weightFlow = dao.getWeightEntriesSinceFlow(com.apexfit.app.utils.getDateDaysAgo(90))
            .flowOn(Dispatchers.IO)
        // AUDIT FIX (BUG-V4-014): honour isManual.
        // Manual users  → stored value (manualCals).
        // Auto users    → raw adaptive TDEE via calcAdaptiveTDEE with emptyList()
        //                 for nutritionLog so it falls back to Mifflin-St Jeor.
        //                 We intentionally do NOT apply suggestCaloricTarget here;
        //                 rings show raw TDEE, coaching shows goal-adjusted — that
        //                 divergence is a documented product decision (§9).
        val completedTodayFlow: Flow<Int> = dao
            .getCompletedSessionCountFlow(com.apexfit.app.utils.DateTimeUtils.todayDateString())
            .distinctUntilChanged()

        val activePlanFlow: kotlinx.coroutines.flow.Flow<com.apexfit.app.data.WorkoutPlan?> =
            dao.getActivePlanFlow()

        val activePlanSessionsFlow: kotlinx.coroutines.flow.Flow<List<com.apexfit.app.data.PlanSession>> =
            activePlanFlow.flatMapLatest { plan ->
                if (plan != null) dao.getSessionsForPlanFlow(plan.id)
                else kotlinx.coroutines.flow.flowOf(emptyList())
            }

        @Suppress("UNCHECKED_CAST")
        combine(
            ds.calorieTargetManualFlow,
            ds.calorieTargetValueFlow,
            ds.goalFlow,
            weightFlow,
            completedTodayFlow,
            activePlanSessionsFlow,
            bioProfileFlow
        ) { values ->
            val isManual            = values[0] as Boolean
            val manualCals          = values[1] as Int
            val goal                = values[2] as String
            @Suppress("UNCHECKED_CAST")
            val weights             = values[3] as List<WeightEntry>
            val completedTodayCount = values[4] as Int
            @Suppress("UNCHECKED_CAST")
            val planSessions        = values[5] as List<com.apexfit.app.data.PlanSession>
            val bio                 = values[6] as BioProfile

            val latestWeight = weights.maxByOrNull { it.date }?.weight
                ?: com.apexfit.app.UserDefaults.WEIGHT_KG
            val calories: Int = if (isManual) {
                manualCals
            } else {
                val recentWeights = weights
                    .sortedByDescending { it.date }
                    .take(14)
                    .map { com.apexfit.app.utils.WeightEntry(date = it.date, weight = it.weight) }
                val tdeeResult = AlgorithmEngine.calcAdaptiveTDEE(
                    weightLog      = recentWeights,
                    nutritionLog   = emptyList(),
                    heightCm       = bio.heightCm,
                    ageYears       = bio.ageYears,
                    biologicalSex  = bio.biologicalSex,
                    weeklyWorkouts = bio.weeklyWorkouts
                )
                // FIX (§9 item 2): apply goal adjustment so Home ring, Nutrition
                // ring, and coaching notification all track against the same target.
                AlgorithmEngine.suggestCaloricTarget(
                    tdee            = tdeeResult.tdee ?: com.apexfit.app.UserDefaults.CALORIES,
                    goal            = goal,
                    currentWeightKg = latestWeight,
                    goalWeightKg    = bio.goalWeightKg,
                    heightCm        = bio.heightCm,
                    ageYears        = bio.ageYears,
                    sex             = bio.biologicalSex
                )
            }

            val cycled = AlgorithmEngine.calcCycledTargets(
                weeklyCalorieTarget    = calories,
                weeklyTrainingSessions = bio.weeklyWorkouts,
                goal                   = goal,
                bodyWeightKg           = latestWeight,
                heightCm               = bio.heightCm,
                sex                    = bio.biologicalSex
            )

            val todayDayString = java.time.LocalDate.now().format(DAY_OF_WEEK_FORMAT)
            val todayPlanned = planSessions.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
            val isPlannedTraining = todayPlanned != null &&
                !todayPlanned.label.contains("Rest", ignoreCase = true) &&
                todayPlanned.focus != "Muscle Recovery & Rest"
            val isTodayTraining = completedTodayCount > 0 || isPlannedTraining

            val todayCalories = if (isTodayTraining) cycled.trainingDayCalories else cycled.restDayCalories
            val todayProtein  = if (isTodayTraining) cycled.trainingDayProtein else cycled.restDayProtein
            val todayCarbs    = if (isTodayTraining) cycled.trainingDayCarbs else cycled.restDayCarbs
            val todayFat      = cycled.fat

            NutritionTargets(
                calories               = todayCalories,
                protein                = todayProtein,
                carbs                  = todayCarbs,
                fat                    = todayFat,
                weeklyTrainingSessions = bio.weeklyWorkouts
            )
        }.debounce(300L).flowOn(Dispatchers.IO).shareIn(appScope, SharingStarted.WhileSubscribed(5_000), 1)
    }

    val todayExercisesFlow: StateFlow<List<PlanExercise>> by lazy {
        val dao = database(appContext).fitnessDao()
        val repo = repository(appContext)
        repo.getActivePlan().flatMapLatest { plan ->
            val sessions = if (plan != null) dao.getSessionsForPlanFlow(plan.id) else flowOf(emptyList())
            sessions.flatMapLatest { sessionList ->
                val todayDayString = java.time.LocalDate.now().format(DAY_OF_WEEK_FORMAT)
                val todaySession = sessionList.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
                if (todaySession != null) dao.getExercisesForSessionFlow(todaySession.id) else flowOf(emptyList())
            }
        }
        .flowOn(Dispatchers.IO)
        .catch { e ->
            android.util.Log.e("ServiceLocator", "todayExercisesFlow error", e)
            emit(emptyList())
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
    }

    val complianceScoresFlow: StateFlow<UiComplianceResult> by lazy {
        val mappedNutritionFlow = nutritionEntriesFlow.map { list ->
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
        combine(
            mappedNutritionFlow,
            richSessionsFlow,
            sharedTargetsFlow
        ) { nutrition, sessions, targets ->
            val res = AlgorithmEngine.calcComplianceScores(nutrition, sessions, targets)
            res.toUi()
        }
        .debounce(300L)
        .flowOn(Dispatchers.Default)
        .catch { e ->
            android.util.Log.e("ServiceLocator", "complianceScoresFlow error", e)
            emit(com.apexfit.app.utils.ComplianceResult(calories = 0, protein = 0, training = 0, overall = 0, weakestDay = null).toUi())
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = com.apexfit.app.utils.ComplianceResult(calories = 0, protein = 0, training = 0, overall = 0, weakestDay = null).toUi()
        )
    }

    val streakResultFlow: StateFlow<StreakResult> by lazy {
        val mappedNutritionFlow = nutritionEntriesFlow.map { list ->
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
        combine(
            mappedNutritionFlow,
            richSessionsFlow,
            sharedTargetsFlow
        ) { nutrition, sessions, targets ->
            AlgorithmEngine.calcStreaks(nutrition, sessions, targets)
        }
        .debounce(300L)
        .flowOn(Dispatchers.Default)
        .catch { e ->
            android.util.Log.e("ServiceLocator", "streakResultFlow error", e)
            emit(StreakResult(com.apexfit.app.data.StreakInfo(0), com.apexfit.app.data.StreakInfo(0)))
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StreakResult(com.apexfit.app.data.StreakInfo(0), com.apexfit.app.data.StreakInfo(0))
        )
    }

    fun database(context: Context): AppDatabase =
        database ?: synchronized(this) {
            database ?: AppDatabase.getDatabase(context).also { database = it }
        }

    fun setDatabase(db: AppDatabase?) {
        database = db
    }

    fun dataStore(context: Context): DataStoreManager =
        dataStore ?: synchronized(this) {
            dataStore ?: DataStoreManager(context.applicationContext).also { dataStore = it }
        }

    fun repository(context: Context): FitnessRepository {
        val db = database(context)
        return repository ?: synchronized(this) {
            repository ?: FitnessRepositoryImpl(db, db.fitnessDao(), dataStore(context)).also { repository = it }
        }
    }

    fun reset() {
        synchronized(this) {
            database?.close()
            database = null
            dataStore = null
            repository = null
            _appScope = null
            _appContext = null
        }
    }
}

data class BioProfile(
    val heightCm: Double,
    val ageYears: Int,
    val biologicalSex: String,
    val weeklyWorkouts: Int,
    val goalWeightKg: Double
) {
    companion object {
        val DEFAULT = BioProfile(
            heightCm = com.apexfit.app.UserDefaults.HEIGHT_CM,
            ageYears = com.apexfit.app.UserDefaults.AGE_YEARS,
            biologicalSex = "male",
            weeklyWorkouts = com.apexfit.app.UserDefaults.WEEKLY_WORKOUTS,
            goalWeightKg = com.apexfit.app.UserDefaults.WEIGHT_KG
        )
    }
}
