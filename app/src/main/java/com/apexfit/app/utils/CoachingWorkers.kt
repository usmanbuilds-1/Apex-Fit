package com.apexfit.app.utils

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
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.DataStoreManager
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private fun buildRepository(context: Context): com.apexfit.app.data.repository.FitnessRepositoryImpl {
    val db = com.apexfit.app.data.AppDatabase.getDatabase(context)
    val dao = db.fitnessDao()
    val dataStore = com.apexfit.app.di.ServiceLocator.dataStore(context)
    return com.apexfit.app.data.repository.FitnessRepositoryImpl(db, dao, dataStore)
}

private fun buildDao(context: Context): com.apexfit.app.data.FitnessDao =
    com.apexfit.app.data.AppDatabase.getDatabase(context).fitnessDao()

private fun buildDataStore(context: Context): com.apexfit.app.data.DataStoreManager =
    com.apexfit.app.di.ServiceLocator.dataStore(context)

class DelayedNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "Apex Fit Update"
        val body = inputData.getString("body") ?: "Open your coach file to view updates."
        val id = inputData.getString("id") ?: "apex_notification"
        val today = com.apexfit.app.utils.getTodayDateString()
        if (id == "midday_protein") {
            val dao = buildDao(applicationContext)
            val todayProtein = dao.getNutritionForDate(today).sumOf { it.protein }
            if (todayProtein >= 75.0) return Result.success()
        }
        if (id == "streak_at_risk") {
            val dao = buildDao(applicationContext)
            val hasLoggedToday = dao.getNutritionForDate(today).isNotEmpty()
            if (hasLoggedToday) return Result.success()
        }

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
            "midday_protein" -> "apexfit://screen/nutrition"
            else -> "apexfit://screen/home"
        }
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink))
        intent.setClass(applicationContext, com.apexfit.app.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.apexfit.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val stableNotifId = mapOf(  
            "morning_readiness" to 2001,  
            "midday_protein" to 2002,  
            "evening_coaching" to 2003,  
            "weekly_report" to 2004  
        )  
        notificationManager.notify(stableNotifId[id] ?: id.hashCode(), notification)
        return Result.success()
    }
}

class DailyCoachingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = buildRepository(applicationContext)
        val dao = buildDao(applicationContext)
        val dataStore = buildDataStore(applicationContext)

        // Load logs
        // AUDIT FIX (BUG-V4-017): bound the nutrition read to 30 days —
        // coaching worker math is 14-day windowed; 30 gives headroom.
        val cutoffNutrition = com.apexfit.app.utils.getDateDaysAgo(30)
        val dbNutrition = dao.getNutritionEntriesSince(cutoffNutrition).first()
        val allNutrition = dbNutrition.groupBy { it.date }.map { (date, list) ->
            NutritionEntry(
                date = date,
                calories = list.sumOf { it.calories },
                protein = list.sumOf { it.protein },
                carbs = list.sumOf { it.carbs },
                fat = list.sumOf { it.fat }
            )
        }.sortedBy { it.date }
        
        val dbWeights = dao.getAllWeightEntries()
        val engineWeights = dbWeights.groupBy { it.date }.map { (date, list) ->
            WeightEntry(date, list.map { it.weight }.average())
        }.sortedBy { it.date }

        val cutoff = com.apexfit.app.utils.getDateDaysAgo(90)
        val dbSessions = dao.getRecentCompletedSessions(cutoff)
        val dbSets = dao.getRecentExerciseSets(cutoff)
        val completedSessions = com.apexfit.app.utils.SessionMapper.buildRichSessions(dbSessions, dbSets)

        val isManual = dataStore.calorieTargetManualFlow.first()
        val manualValue = dataStore.calorieTargetValueFlow.first()
        val userGoal = dataStore.goalFlow.first()
        val latestWeightForCoaching = engineWeights.lastOrNull()?.weight ?: com.apexfit.app.UserDefaults.WEIGHT_KG
        val latestTrend = AlgorithmEngine.getCurrentTrendWeight(engineWeights) ?: latestWeightForCoaching
        
        val tdeeResult = AlgorithmEngine.calcAdaptiveTDEE(engineWeights, allNutrition)
        val suggestedCal = AlgorithmEngine.suggestCaloricTarget(tdeeResult.tdee ?: com.apexfit.app.UserDefaults.CALORIES, userGoal)
        
        val calorieTarget = if (isManual) manualValue else suggestedCal
        val targets = com.apexfit.app.utils.AlgorithmEngine.calcMacroTargets(calorieTarget, latestWeightForCoaching, userGoal)

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
        
        repository.scanAndSaveWeeklyPatterns()

        val plateau = AlgorithmEngine.detectPlateau(engineWeights, allNutrition, completedSessions, windowDays = 14)
        
        val allTriggers = mutableListOf<NotificationEngine.NotificationTrigger>()
        
        if (plateau.isPlateaued && plateau.severity == "confirmed") {
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
                targets = targets,
                todaySessionType = todaySessionType,
                currentHour = hour
            )
            allTriggers.addAll(triggersAtHour)
        }

        // Schedule triggers with relative offsets
        allTriggers.distinctBy { it.id }.forEach { trigger ->
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, trigger.triggerHour)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
            val delayMs = (target.timeInMillis - java.util.Calendar.getInstance().timeInMillis).coerceAtLeast(0L)
            val inputData = workDataOf(
                "id" to trigger.id,
                "title" to trigger.title,
                "body" to trigger.body
            )
            val delayedRequest = OneTimeWorkRequestBuilder<DelayedNotificationWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
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

class PatternScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repository = buildRepository(applicationContext)
        return try {
            repository.scanAndSaveWeeklyPatterns()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("PatternScanWorker", "Scan failed", e)
            Result.failure()
        }
    }
}