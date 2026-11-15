package com.apexfit.app.utils

import android.content.Context
import java.util.Calendar
import kotlin.math.roundToInt

object NotificationEngine {

    fun hasNotificationPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    data class NotificationTrigger(
        val id: String,
        val title: String,
        val body: String,
        val triggerHour: Int,
        val priority: String
    )

    fun evaluateDailyTriggers(
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        targets: NutritionTargets,
        todaySessionType: String?,
        currentHour: Int
    ): List<NotificationTrigger> {
        val triggers = mutableListOf<NotificationTrigger>()
        val today = getCurrentDate()
        val todayNutrition = nutritionLog.filter { it.date == today }
        val todayProtein = todayNutrition.sumOf { it.protein }.toDouble()

        // TRIGGER 1 — Morning readiness (7am)
        if (currentHour in 6..9 && todaySessionType != null) {
            triggers.add(NotificationTrigger(
                id = "morning_readiness",
                title = "Session readiness calculated",
                body = "You have $todaySessionType today. Open APEX FIT to see your readiness score.",
                triggerHour = 7,
                priority = "default"
            ))
        }

        // TRIGGER 2 — Midday protein progress (12pm)
        if (currentHour in 11..13 && targets.protein > 0) {
            val morningProtein = todayProtein
            val proteinTarget = targets.protein.toDouble()
            if (morningProtein < proteinTarget * 0.3) {
                triggers.add(NotificationTrigger(
                    id = "midday_protein",
                    title = "Protein progress",
                    body = "You've logged ${morningProtein.roundToInt()}g of ${proteinTarget.roundToInt()}g today. If useful, plan a protein-rich meal later.",
                    triggerHour = 12,
                    priority = "default"
                ))
            }
        }

        // TRIGGER 3 — Optional evening log reminder (no streak pressure / fake urgency)
        if (currentHour in 19..21 && todayNutrition.isEmpty()) {
            triggers.add(NotificationTrigger(
                id = "evening_nutrition_reminder",
                title = "No nutrition logged today",
                body = "Log meals if you want today's nutrition totals included in your trends.",
                triggerHour = 20,
                priority = "default"
            ))
        }

        // TRIGGER 4 — Sunday weekly report (9am)
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && currentHour in 8..10) {
            triggers.add(NotificationTrigger(
                id = "weekly_report",
                title = "Your weekly training summary is ready",
                body = "Review this week's training, nutrition and progress trends.",
                triggerHour = 9,
                priority = "default"
            ))
        }

        return triggers
    }
}
