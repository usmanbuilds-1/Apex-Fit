package com.apexfit.app.utils

import com.apexfit.app.data.NutritionEntry
import com.apexfit.app.data.TrainingSession
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
                    name = "Breakfast",
                    time = "09:00",
                    calories = 300,
                    protein = 20.0,
                    carbs = 35.0,
                    fat = 8.0
                )
            ),
            trainingLog = emptyList<TrainingSession>(),
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
}
