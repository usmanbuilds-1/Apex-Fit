package com.apexfit.app.utils

import com.apexfit.app.data.NutritionEntry
import com.apexfit.app.data.NutritionTargets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEngineTest {

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
