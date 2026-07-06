package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
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

        val deepLink = when (id) {
            "weekly_report", "plateau_confirmed" -> "apexfit://screen/progress"
            "streak_at_risk" -> "apexfit://screen/train"
            "midday_protein_check" -> "apexfit://screen/nutrition"
            else -> "apexfit://screen/home"
        }
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink))
        intent.setClass(applicationContext, com.example.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.example.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
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

        val isManual = dataStore.calorieTargetManualFlow.first()
        val manualValue = dataStore.calorieTargetValueFlow.first()
        val userGoal = dataStore.goalFlow.first()
        val latestWeightForCoaching = engineWeights.lastOrNull()?.weight ?: com.example.UserDefaults.WEIGHT_KG
        val latestTrend = AlgorithmEngine.getCurrentTrendWeight(engineWeights) ?: latestWeightForCoaching
        
        val tdeeResult = AlgorithmEngine.calcAdaptiveTDEE(engineWeights, allNutrition)
        val suggestedCal = AlgorithmEngine.suggestCaloricTarget(tdeeResult.tdee ?: com.example.UserDefaults.CALORIES, userGoal)
        
        val calTarget = if (isManual) manualValue else suggestedCal
        val proteinTarget = (latestTrend * 2.0).toInt().coerceIn(100, 250) // 2.0g/kg for athletes
        val fatTarget = (calTarget * 0.25 / 9.0).toInt().coerceIn(45, 120)
        val carbsTarget = ((calTarget - (proteinTarget * 4) - (fatTarget * 9)) / 4).toInt().coerceIn(100, 500)

        val targets = NutritionTargets(
            calories = calTarget,
            protein = proteinTarget,
            carbs = carbsTarget,
            fat = fatTarget,
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

        val workManager = WorkManager.getInstance(applicationContext)

        // Evaluate triggers for each key hour of the day
        val keyHours = listOf(7, 9, 12, 20)
        
        val repository = com.example.data.repository.FitnessRepositoryImpl(db, dao, dataStore)
        repository.scanAndSaveWeeklyPatterns()

        val plateau = AlgorithmEngine.detectPlateau(engineWeights, allNutrition, completedSessions, windowDays = 14)
        
        val allTriggers = mutableListOf<NotificationEngine.NotificationTrigger>()
        
        if (plateau.plateau && plateau.severity == "confirmed") {
            allTriggers.add(NotificationEngine.NotificationTrigger(
                id = "plateau_confirmed",
                title = "Weight plateau confirmed — 14 days",
                body = "Your trend weight has not moved despite consistent logging. Your coach has a specific intervention ready.",
                triggerHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                priority = "urgent"
            ))
        }

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
            workManager?.enqueueUniqueWork(
                "delayed_${trigger.id}",
                androidx.work.ExistingWorkPolicy.REPLACE,
                delayedRequest
            )
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
            val wm = WorkManager.getInstance(context)
            
            wm.enqueueUniquePeriodicWork(
                "daily_coaching_notifications",
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyWorkRequest
            )
        } catch (ex: Throwable) {
            android.util.Log.e("CoachingScheduler", "Failed to schedule periodic coaching task", ex)
        }
    }
}
