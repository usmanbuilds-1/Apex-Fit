package com.example.utils

import java.util.Calendar

object NotificationEngine {

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
        weightLog: List<WeightEntry>,
        targets: NutritionTargets,
        todaySessionType: String?,
        currentHour: Int
    ): List<NotificationTrigger> {
        val triggers = mutableListOf<NotificationTrigger>()
        val today = getCurrentDate()
        val todayNutrition = nutritionLog.find { it.date == today }

        // TRIGGER 1 — Morning readiness (7am)
        if (currentHour == 7 && todaySessionType != null) {
            triggers.add(NotificationTrigger(
                id = "morning_readiness",
                title = "Session readiness calculated",
                body = "You have $todaySessionType today. Open APEX FIT to see your readiness score.",
                triggerHour = 7,
                priority = "default"
            ))
        }

        // TRIGGER 2 — Midday protein check (12pm)
        if (currentHour == 12) {
            val morningProtein = (todayNutrition?.protein ?: 0).toDouble()
            val proteinTarget = targets.protein.toDouble()
            if (morningProtein < proteinTarget * 0.3) {
                val remaining = proteinTarget - morningProtein
                triggers.add(NotificationTrigger(
                    id = "midday_protein",
                    title = "Only ${morningProtein.toInt()}g protein by noon",
                    body = "You need ${remaining.toInt()}g more today. Front-loading protein prevents evening scrambling. Eat 40g now.",
                    triggerHour = 12,
                    priority = "high"
                ))
            }
        }

        // TRIGGER 3 — Streak at risk (8pm)
        if (currentHour == 20) {
            val todayLogged = todayNutrition != null
            if (!todayLogged) {
                val streaks = AlgorithmEngine.calcStreaks(nutritionLog, trainingLog, targets)
                if (streaks.nutrition.current >= 3) {
                    triggers.add(NotificationTrigger(
                        id = "streak_risk",
                        title = "Your ${streaks.nutrition.current}-day streak ends at midnight",
                        body = "You have not logged nutrition today. 2 minutes of logging preserves your streak.",
                        triggerHour = 20,
                        priority = "high"
                    ))
                }
            }
        }

        // TRIGGER 4 — Sunday weekly report (9am)
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && currentHour == 9) {
            triggers.add(NotificationTrigger(
                id = "weekly_report",
                title = "Your weekly coaching report is ready",
                body = "Your coach has analysed your week. See what to adjust for maximum progress.",
                triggerHour = 9,
                priority = "default"
            ))
        }

        return triggers
    }
}
