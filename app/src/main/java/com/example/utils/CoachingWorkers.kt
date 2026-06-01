package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DelayedNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "Apex Fit Update"
        val body = inputData.getString("body") ?: "Open your coach file to view updates."
        val id = inputData.getString("id") ?: "apex_notification"

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "apex_fitness_coaching"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Apex Coaching",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id.hashCode(), notification)
        return Result.success()
    }
}

class DailyCoachingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.fitnessDao()
        val dataStore = DataStoreManager(applicationContext)

        // Load logs
        val dbNutrition = dao.getAllNutritionEntriesFlow().first()
        val allNutrition = dbNutrition.groupBy { it.date }.map { (date, list) ->
            NutritionEntry(
                date = date,
                calories = list.sumOf { it.calories },
                protein = list.sumOf { it.protein }.toInt(),
                carbs = list.sumOf { it.carbs }.toInt(),
                fat = list.sumOf { it.fat }.toInt()
            )
        }.sortedBy { it.date }
        
        val dbWeights = dao.getAllWeightEntries()
        val engineWeights = dbWeights.groupBy { it.date }.map { (date, list) ->
            WeightEntry(date, list.map { it.weight }.average())
        }.sortedBy { it.date }

        val dbSessions = dao.getAllTrainingSessions()
        val completedSessions = dbSessions.map { session ->
            val dbSets = dao.getSetsForSession(session.id)
            val exerciseLogs = dbSets.groupBy { it.exerciseId }.map { (exId, sets) ->
                val firstSet = sets.firstOrNull()
                val name = firstSet?.exerciseName ?: "Exercise"
                val muscle = firstSet?.muscleGroup ?: "General"
                ExerciseLog(
                    id = exId,
                    name = name,
                    muscleGroup = muscle,
                    sets = sets.map { s ->
                        ExerciseSet(
                            weight = s.weight,
                            reps = s.reps,
                            rpe = s.rpe,
                            isWarmup = s.isWarmup,
                            completed = s.completed
                        )
                    }
                )
            }
            TrainingSession(
                date = session.date,
                sessionType = session.sessionType,
                completed = session.completed,
                sessionFeel = session.sessionFeel,
                durationMinutes = session.durationMinutes,
                exercises = exerciseLogs
            )
        }

        val calTarget = dataStore.calorieTargetValueFlow.first()
        val targets = NutritionTargets(
            calories = calTarget,
            protein = 160,
            carbs = 280,
            fat = 75,
            weeklyTrainingSessions = 4
        )

        // Find today session
        val activePlan = dao.getActivePlan()
        var todaySessionType: String? = null
        if (activePlan != null) {
            val sessionsPlan = dao.getSessionsForPlan(activePlan.id)
            val todayDayString = SimpleDateFormat("EEEE", Locale.US).format(Date())
            val todaySession = sessionsPlan.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
            if (todaySession != null) {
                todaySessionType = todaySession.label
            }
        }

        val workManager = try {
            WorkManager.getInstance(applicationContext)
        } catch (e: Throwable) {
            try {
                val config = androidx.work.Configuration.Builder()
                    .setMinimumLoggingLevel(android.util.Log.INFO)
                    .build()
                WorkManager.initialize(applicationContext, config)
                WorkManager.getInstance(applicationContext)
            } catch (innerEx: Throwable) {
                android.util.Log.e("DailyCoachingWorker", "Could not initialize WorkManager manually", innerEx)
                null
            }
        }

        // Evaluate triggers for each key hour of the day
        val keyHours = listOf(7, 12, 17, 20, 22)
        
        val allTriggers = mutableListOf<NotificationEngine.NotificationTrigger>()
        keyHours.forEach { hour ->
            val triggersAtHour = NotificationEngine.evaluateDailyTriggers(
                nutritionLog = allNutrition,
                trainingLog = completedSessions,
                weightLog = engineWeights,
                targets = targets,
                todaySessionType = todaySessionType,
                currentHour = hour
            )
            allTriggers.addAll(triggersAtHour)
        }

        // Schedule triggers with relative offsets
        allTriggers.distinctBy { it.id }.forEach { trigger ->
            val delayHours = (trigger.triggerHour - 6).coerceAtLeast(0)
            val inputData = workDataOf(
                "id" to trigger.id,
                "title" to trigger.title,
                "body" to trigger.body
            )
            val delayedRequest = OneTimeWorkRequestBuilder<DelayedNotificationWorker>()
                .setInitialDelay(delayHours.toLong(), TimeUnit.HOURS)
                .setInputData(inputData)
                .build()
            workManager?.enqueue(delayedRequest)
        }

        return Result.success()
    }
}

object CoachingScheduler {
    fun schedule6AmDailyCoachingTask(context: Context) {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelay = dueDate.timeInMillis - currentDate.timeInMillis
        
        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyCoachingWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
            
        try {
            val wm = try {
                WorkManager.getInstance(context)
            } catch (e: Throwable) {
                try {
                    val config = androidx.work.Configuration.Builder()
                        .setMinimumLoggingLevel(android.util.Log.INFO)
                        .build()
                    WorkManager.initialize(context, config)
                    WorkManager.getInstance(context)
                } catch (innerEx: Throwable) {
                    android.util.Log.e("CoachingScheduler", "Could not initialize WorkManager manually", innerEx)
                    null
                }
            }
            
            wm?.enqueueUniquePeriodicWork(
                "daily_coaching_notifications",
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyWorkRequest
            )
        } catch (ex: Throwable) {
            android.util.Log.e("CoachingScheduler", "Failed to schedule periodic coaching task", ex)
        }
    }
}
