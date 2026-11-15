package com.apexfit.app.utils

import com.apexfit.app.data.NutritionEntry
import com.apexfit.app.data.NutritionTargets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEngineTest {
    private val targets = NutritionTargets(
        calories = 2400,
        protein = 160,
        carbs = 300,
        fat = 70
    )

    @Test
    fun eveningReminderDoesNotUseStreakPressure() {
        val triggers = NotificationEngine.evaluateDailyTriggers(
            nutritionLog = emptyList(),
            trainingLog = emptyList(),
            targets = targets,
            todaySessionType = null,
            currentHour = 20
        )

        val evening = triggers.firstOrNull { it.id == "evening_nutrition_reminder" }
        assertTrue(evening != null)
        val combined = "${evening?.title} ${evening?.body}".lowercase()
        assertFalse(combined.contains("streak"))
        assertFalse(combined.contains("midnight"))
        assertFalse(combined.contains("preserve"))
    }

    @Test
    fun middayProteinReminderIsNeutralAndNonPrescriptive() {
        val triggers = NotificationEngine.evaluateDailyTriggers(
            nutritionLog = listOf(
                NutritionEntry(
                    date = getCurrentDate(),
                    calories = 300,
                    protein = 20.0,
                    carbs = 35.0,
                    fat = 8.0
                )
            ),
            trainingLog = emptyList(),
            targets = targets,
            todaySessionType = null,
            currentHour = 12
        )

        val reminder = triggers.firstOrNull { it.id == "midday_protein" }
        assertTrue(reminder != null)
        val combined = "${reminder?.title} ${reminder?.body}".lowercase()
        assertFalse(combined.contains("eat 40g now"))
        assertFalse(combined.contains("only "))
        assertFalse(combined.contains("high"))
    }

    @Test
    fun middayProteinAggregatesAllMealsLoggedToday() {
        val today = getCurrentDate()
        val nutrition = listOf(
            NutritionEntry(date = today, calories = 400, protein = 20, carbs = 50, fat = 10),
            NutritionEntry(date = today, calories = 500, protein = 20, carbs = 60, fat = 15)
        )

        val triggers = NotificationEngine.evaluateDailyTriggers(
            nutritionLog = nutrition,
            trainingLog = emptyList(),
            targets = NutritionTargets(2200, 120, 250, 70),
            todaySessionType = null,
            currentHour = 12
        )

        assertFalse(
            "40g total protein is above 30% of a 120g target; first-meal-only logic must not fire",
            triggers.any { it.id == "midday_protein" }
        )
    }

    @Test
    fun middayProteinStillFiresWhenDailyTotalIsBelowThreshold() {
        val today = getCurrentDate()
        val nutrition = listOf(
            NutritionEntry(date = today, calories = 250, protein = 10, carbs = 30, fat = 8),
            NutritionEntry(date = today, calories = 250, protein = 15, carbs = 30, fat = 8)
        )

        val triggers = NotificationEngine.evaluateDailyTriggers(
            nutritionLog = nutrition,
            trainingLog = emptyList(),
            targets = NutritionTargets(2200, 120, 250, 70),
            todaySessionType = null,
            currentHour = 12
        )

        assertTrue(triggers.any { it.id == "midday_protein" })
    }
}
