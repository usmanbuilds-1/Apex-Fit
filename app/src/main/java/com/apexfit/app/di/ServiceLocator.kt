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
import com.apexfit.app.utils.SessionMapper
import com.apexfit.app.utils.getDateDaysAgo
import com.apexfit.app.ui.models.UiSessionReadiness
import com.apexfit.app.ui.models.toUi
import kotlin.math.roundToInt

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

    val sessionReadinessFlow: StateFlow<UiSessionReadiness?> by lazy {
        val dao = database(appContext).fitnessDao()
        combine(
            richSessionsFlow,
            dataStore(appContext).calorieTargetValueFlow,
            dataStore(appContext).currentWeightFlow,
            dao.getAllNutritionEntriesFlow()
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
