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
import com.apexfit.app.utils.SessionMapper
import com.apexfit.app.utils.getDateDaysAgo
import com.apexfit.app.ui.models.UiSessionReadiness
import com.apexfit.app.ui.models.toUi
import kotlin.math.roundToInt
import com.apexfit.app.utils.AlgorithmEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
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

    val richSessionsFlow: StateFlow<List<RichTrainingSession>> by lazy {
        val dao = database(appContext).fitnessDao()
        val cutoff = getDateDaysAgo(90)
        combine(
            dao.getRecentCompletedSessionsFlow(cutoff),
            dao.getRecentExerciseSetsFlow(cutoff)
        ) { sessions, sets ->
            SessionMapper.buildRichSessions(sessions, sets)
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    }

    val weightEntriesFlow: StateFlow<List<com.apexfit.app.utils.WeightEntry>> by lazy {
        val dao = database(appContext).fitnessDao()
        dao.getAllWeightEntriesFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )
    }

    val nutritionEntriesFlow: StateFlow<List<com.apexfit.app.utils.NutritionEntry>> by lazy {
        val dao = database(appContext).fitnessDao()
        dao.getNutritionEntriesSince(
            com.apexfit.app.utils.getDateDaysAgo(90)
        )
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
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
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    }

    val sharedTargetsFlow: Flow<NutritionTargets> by lazy {
        val dao = database(appContext).fitnessDao()
        val ds = dataStore(appContext)
        val weightFlow = dao.getAllWeightEntriesFlow()
            .flowOn(Dispatchers.IO)
        // AUDIT FIX (BUG-V4-014): honour isManual.
        // Manual users  → stored value (manualCals).
        // Auto users    → raw adaptive TDEE via calcAdaptiveTDEE with emptyList()
        //                 for nutritionLog so it falls back to Mifflin-St Jeor.
        //                 We intentionally do NOT apply suggestCaloricTarget here;
        //                 rings show raw TDEE, coaching shows goal-adjusted — that
        //                 divergence is a documented product decision (§9).
        combine(
            ds.calorieTargetManualFlow,
            ds.calorieTargetValueFlow,
            ds.goalFlow,
            weightFlow
        ) { isManual, manualCals, goal, weights ->
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
                    weightLog = recentWeights,
                    nutritionLog = emptyList()
                )
                // FIX (§9 item 2): apply goal adjustment so Home ring, Nutrition
                // ring, and coaching notification all track against the same target.
                AlgorithmEngine.suggestCaloricTarget(
                    tdeeResult.tdee ?: com.apexfit.app.UserDefaults.CALORIES,
                    goal
                )
            }
            AlgorithmEngine.calcMacroTargets(calories, latestWeight, goal)
        }.flowOn(Dispatchers.IO).shareIn(appScope, SharingStarted.Lazily, 1)
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
