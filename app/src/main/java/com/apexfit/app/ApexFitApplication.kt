package com.apexfit.app

import android.app.Application
import androidx.work.Configuration
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.utils.SeedService
import com.apexfit.app.utils.CoachingScheduler

class ApexFitApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val app = this

        com.apexfit.app.di.ServiceLocator.setAppScope(appScope, this)

        // Seed exercises on first launch
        appScope.launch {
            try {
                val dataStore = DataStoreManager.getInstance(app)
                val isSeeded = dataStore.isExercisesSeededFlow.firstOrNull() ?: false
                if (!isSeeded) {
                    SeedService.seed(app)
                    dataStore.setExercisesSeeded(true)
                }
            } catch (e: Exception) {
                Log.e("ApexFitApplication", "Seeding failed", e)
            }
        }

        // Schedule coaching notifications — with delay to ensure WorkManager is initialized
        appScope.launch {
            delay(2000)
            try {
                CoachingScheduler.schedule6AmDailyCoachingTask(app)
                Log.i("ApexFitApplication", "Coaching task scheduled")
            } catch (e: Exception) {
                Log.e("ApexFitApplication", "Failed to schedule coaching", e)
            }
        }
    }
}
