package com.apexfit.app.utils

import com.apexfit.app.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ComplianceEvidenceTest {
    private val today = LocalDate.parse(getCurrentDate())
    private val targets = NutritionTargets(2000, 120, 200, 60, weeklyTrainingSessions = 0)
    private fun meal(offset: Long, calories: Int = 2000) =
        NutritionEntry(date = today.minusDays(offset).toString(), calories = calories, protein = 120, carbs = 200, fat = 60)

    @Test
    fun onlyConfirmedFinishedDaysAreComparedWithCurrentTargets() {
        val logs = listOf(meal(3), meal(2, 400), meal(1), meal(0, 100), meal(-1))
        val confirmations = NutritionDayEvidence.snapshots(listOf(logs[0], logs[2], logs[3], logs[4]))
        val result = ComplianceEngine.calcComplianceScores(logs, emptyList(), targets,
            nutritionConfirmations = confirmations, today = today)
        assertEquals(2, result.confirmedNutritionDays)
        assertEquals(3, result.evaluatedDays)
        assertEquals(67, result.nutritionCoverage)
        assertEquals(100, result.calories)
        assertEquals(100, result.overall)
        assertTrue(result.nutritionComparisonAvailable)
        assertFalse(result.trainingComparisonAvailable)
    }

    @Test
    fun editedConfirmedLogIsUnknownRatherThanFailed() {
        val before = listOf(meal(1))
        val result = ComplianceEngine.calcComplianceScores(listOf(meal(1, 100)), emptyList(), targets,
            nutritionConfirmations = NutritionDayEvidence.snapshots(before), today = today)
        assertFalse(result.nutritionComparisonAvailable)
        assertEquals(0, result.confirmedNutritionDays)
        assertNull(result.weakestDay)
    }

    @Test
    fun todayDoesNotBreakConfirmedLoggingStreak() {
        val logs = listOf(meal(3), meal(2), meal(1), meal(0, 100))
        val result = ComplianceEngine.calcStreaks(logs, emptyList(), targets,
            nutritionConfirmations = NutritionDayEvidence.snapshots(logs.take(3)), today = today)
        assertEquals(3, result.nutrition.current)
    }

    @Test
    fun trendNeedsAdequateSpanAndIgnoresFutureWeight() {
        val stable = (0..13).map { WeightEntry(date = today.minusDays(it.toLong()).toString(), weight = 80.0) }
        val future = WeightEntry(date = today.plusDays(1).toString(), weight = 120.0)
        assertEquals("maintaining", AlgorithmEngine.getWeightDirection(stable + future))
        assertEquals(80.0, AlgorithmEngine.getCurrentTrendWeight(stable + future)!!, 0.001)
        assertEquals("insufficient_data", AlgorithmEngine.getWeightDirection(stable.take(2)))
        assertEquals("insufficient_data", AlgorithmEngine.getWeightDirection(stable.drop(4)))
        val gaining = (0..13).map { WeightEntry(date = today.minusDays(it.toLong()).toString(), weight = 82.0 - 0.2 * it) }
        assertEquals("gaining", AlgorithmEngine.getWeightDirection(gaining))
    }
}
