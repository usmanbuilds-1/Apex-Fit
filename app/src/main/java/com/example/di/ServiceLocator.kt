package com.example.di

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.data.repository.FitnessRepositoryImpl
import com.example.domain.repository.FitnessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.example.data.RichTrainingSession
import com.example.utils.SessionMapper
import com.example.utils.getDateDaysAgo

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

    private val appScope get() = _appScope
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

    fun database(context: Context): AppDatabase =
        database ?: synchronized(this) {
            database ?: AppDatabase.getDatabase(context).also { database = it }
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
            database = null
            dataStore = null
            repository = null
            _appScope = null
            _appContext = null
        }
    }
}
