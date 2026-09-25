package com.apexfit.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
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
import com.apexfit.app.utils.CoachingScheduler
import com.apexfit.app.utils.CoachingWorkers
import com.apexfit.app.utils.RestTimerAlarmReceiver
import com.apexfit.app.utils.SeedService

class ApexFitApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
        super.onCreate()
        val app = this

        createNotificationChannels()

        com.apexfit.app.di.ServiceLocator.setAppScope(appScope, this)

        com.apexfit.app.utils.WorkoutActiveNotification.createChannel(this)

        // Startup initialization: seed exercises and schedule coaching sequentially
        appScope.launch {
            val dataStore = DataStoreManager.getInstance(app)

            // Seeding check
            try {
                val isSeeded = dataStore.isExercisesSeededFlow.firstOrNull() ?: false
                val seedVersion = dataStore.exerciseSeedVersionFlow.firstOrNull() ?: 0
                if (!isSeeded || seedVersion < SeedService.CURRENT_SEED_VERSION) {
                    SeedService.seed(app)
                }
            } catch (e: Exception) {
                Log.e("ApexFitApplication", "Seeding failed", e)
            }

            // Weight normalization (sequential, not parallel)
            try {
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

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val coachingChannel = NotificationChannel(
                CoachingWorkers.CHANNEL_ID,
                "Coaching Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val restTimerChannel = NotificationChannel(
                RestTimerAlarmReceiver.CHANNEL_ID,
                "Rest Timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when rest timer completes"
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannels(listOf(coachingChannel, restTimerChannel))
        }
    }
}
