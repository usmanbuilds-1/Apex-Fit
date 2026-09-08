package com.apexfit.app

import android.app.Application
import androidx.work.Configuration
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

        com.apexfit.app.utils.WorkoutActiveNotification.createChannel(this)

        // Seed exercises on first launch or upgrade to seed version 2
        appScope.launch {
            try {
                val dataStore = DataStoreManager.getInstance(app)
                val isSeeded = dataStore.isExercisesSeededFlow.firstOrNull() ?: false
                val seedVersion = dataStore.exerciseSeedVersionFlow.firstOrNull() ?: 0
                if (!isSeeded || seedVersion < SeedService.CURRENT_SEED_VERSION) {
                    SeedService.seed(app)
                }
            } catch (e: Exception) {
                Log.e("ApexFitApplication", "Seeding failed", e)
            }
        }

        // Schedule coaching notifications
        appScope.launch {
            try {
                val dataStore = com.apexfit.app.di.ServiceLocator.dataStore(app)
                val alreadyNormalized = dataStore.weightsNormalizedFlow.first()
                if (!alreadyNormalized) {
                    dataStore.normalizeDataStoreWeights()
                }

                // AUDIT FIX (BUG-V4-015): canonicalize exercise_set and PR weights for
                // users who had lb data stored by a pre-v21 build.
                val dao = com.apexfit.app.di.ServiceLocator.database(app).fitnessDao()
                val isImperial = dataStore.unitsFlow.first()
                    .lowercase() in listOf("lb", "lbs")
                dataStore.canonicalizeExerciseSetWeightsIfNeeded(dao, isImperial)

                // FIX (§9 item 5): canonicalize plan_exercises weights on first v22 launch
                dataStore.canonicalizePlanExerciseWeightsIfNeeded(dao, isImperial)

                CoachingScheduler.schedule6AmDailyCoachingTask(app)
                Log.i("ApexFitApplication", "Coaching task scheduled")
            } catch (e: Exception) {
                Log.e("ApexFitApplication", "Failed to schedule coaching", e)
            }
        }
    }
}
