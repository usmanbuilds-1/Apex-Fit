package com.example.utils

import com.example.data.RichTrainingSession
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar

class AlgorithmEngineTest {

    private fun d(daysAgo: Int): String = getDateDaysAgo(daysAgo)

    @Test
    fun testCalcAdaptiveTDEE_normalInput() {
        val wLog = (0..14).map {
            WeightEntry(date = d(14 - it), weight = 80.0 - 0.1 * it)
        }
        val nLog = (0..14).map {
            NutritionEntry(date = d(it), calories = 2500, protein = 150, carbs = 250, fat = 80)
        }
        val res = AlgorithmEngine.calcAdaptiveTDEE(wLog, nLog, windowDays = 14)

        val recentWeight = wLog.sortedBy { it.date }
        val avgCalories = nLog.map { it.calories }.average()
        val trendData = AlgorithmEngine.calcTrendWeight(recentWeight)
        val weightChangeKg = trendData.last().trend - trendData.first().trend
        val daysBetween = getDaysBetween(recentWeight.first().date, recentWeight.last().date).coerceAtLeast(1L)
        val expectedImbalance = (weightChangeKg * 7700.0) / daysBetween
        val expectedTdee = (avgCalories - expectedImbalance).toInt()

        assertEquals(expectedTdee, res.tdee)
    }

    @Test
    fun testCalcAdaptiveTDEE_edgeCase_empty() {
        val res = AlgorithmEngine.calcAdaptiveTDEE(emptyList(), emptyList())
        assertTrue((res.tdee ?: 0) > 0) // Should fallback to BMR
    }

    @Test
    fun testSuggestCaloricTarget_normal() {
        val res = AlgorithmEngine.suggestCaloricTarget(2500, "lose weight", 0.5)
        // 0.5kg/week = 3850 kcal/week = 550 kcal/day deficit => 1950
        assertEquals(1950, res)
    }

    @Test
    fun testSuggestCaloricTarget_edgeCase_emptyGoal() {
        val res = AlgorithmEngine.suggestCaloricTarget(2500, "", 0.5)
        assertEquals(2500, res) // maintain
    }

    @Test
    fun testCalcEffectiveSets_normal() {
        val exSet = ExerciseSet(weight = 100.0, reps = 10, rpe = 8, isWarmup = false, completed = true)
        val exLog = ExerciseLog(id = "bench", name = "Bench", muscleGroup = "chest", sets = listOf(exSet))
        val tLog = listOf(RichTrainingSession(date = d(0), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(exLog)))
        val res = AlgorithmEngine.calcEffectiveSets(tLog, 7)
        // RPE 8 => 0.75 effective sets
        assertEquals(0.75, res["chest"] ?: 0.0, 0.01)
    }

    @Test
    fun testCalcEffectiveSets_boundary_RPE7() {
        val exSet = ExerciseSet(weight = 100.0, reps = 10, rpe = 7, isWarmup = false, completed = true)
        val exLog = ExerciseLog(id = "bench", name = "Bench", muscleGroup = "chest", sets = listOf(exSet))
        val tLog = listOf(RichTrainingSession(date = d(0), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(exLog)))
        val res = AlgorithmEngine.calcEffectiveSets(tLog, 7)
        // RPE 7 => 0.5 effective sets
        assertEquals(0.5, res["chest"] ?: 0.0, 0.01)
    }

    @Test
    fun testCalcEffectiveSets_edgeCase_empty() {
        val res = AlgorithmEngine.calcEffectiveSets(emptyList(), 7)
        assertTrue(res.isEmpty())
    }

    @Test
    fun testDetectPlateau_normal() {
        val wLog = (0..14).map { WeightEntry(date = d(14 - it), weight = 80.0) }
        val nLog = (0..14).map { NutritionEntry(date = d(14 - it), calories = 2000, protein = 150, carbs = 200, fat = 60) }

        val exSet = ExerciseSet(weight = 100.0, reps = 10, rpe = 8, isWarmup = false, completed = true)
        val exLog = ExerciseLog(id = "bench", name = "Bench", muscleGroup = "chest", sets = listOf(exSet))
        val earlierSession = RichTrainingSession(date = d(20), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(exLog))
        val recentSession = RichTrainingSession(date = d(2), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(exLog))

        // Case 1: maintained volume (1000 vs 1000) -> true plateau
        val resPlateau = AlgorithmEngine.detectPlateau(wLog, nLog, listOf(earlierSession, recentSession), windowDays = 14)
        assertTrue(resPlateau.plateau)

        // Case 2: volume dropped >=5% (500 vs 1000) -> not a plateau
        val exSetLow = ExerciseSet(weight = 50.0, reps = 10, rpe = 8, isWarmup = false, completed = true)
        val exLogLow = ExerciseLog(id = "bench", name = "Bench", muscleGroup = "chest", sets = listOf(exSetLow))
        val recentSessionLow = RichTrainingSession(date = d(2), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(exLogLow))

        val resNoPlateau = AlgorithmEngine.detectPlateau(wLog, nLog, listOf(earlierSession, recentSessionLow), windowDays = 14)
        assertFalse(resNoPlateau.plateau)
    }

    @Test
    fun testDetectPlateau_edgeCase_empty() {
        val res = AlgorithmEngine.detectPlateau(emptyList(), emptyList(), emptyList(), windowDays = 10)
        assertFalse(res.plateau)
    }

    @Test
    fun testCalcFatigueToFitness_normal() {
        val tLog = (0..14).map { 
            val exSet = ExerciseSet(weight = 100.0, reps = 10, rpe = 8, isWarmup = false, completed = true)
            val exLog = ExerciseLog(id = "squat", name = "Squat", muscleGroup = "quads", sets = listOf(exSet))
            RichTrainingSession(date = d(14 - it), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(exLog)) 
        }
        val res = AlgorithmEngine.calcFatigueToFitness(tLog)
        assertNotNull(res.ratio)
    }

    @Test
    fun testCalcFatigueToFitness_edgeCase_empty() {
        val res = AlgorithmEngine.calcFatigueToFitness(emptyList())
        assertNull(res.ratio)
    }

    @Test
    fun testCalcComplianceScores_normal() {
        val nLog = (0..13).map { NutritionEntry(date = d(13 - it), calories = 2500, protein = 150, carbs = 200, fat = 60) }
        val targets = NutritionTargets(2500, 150, 200, 60, 4)
        val res = AlgorithmEngine.calcComplianceScores(nLog, emptyList(), targets, 14)
        assertEquals(100, res.protein)
        assertEquals(100, res.calories)
    }

    @Test
    fun testCalcComplianceScores_edgeCase_empty() {
        val res = AlgorithmEngine.calcComplianceScores(emptyList(), emptyList(), NutritionTargets(2500, 150, 200, 60, 4), 14)
        assertEquals(0, res.overall)
    }

    @Test
    fun testCalcDeloadRecommendation_normal() {
        val res = AlgorithmEngine.calcDeloadRecommendation(emptyList(), 100)
        assertEquals("none", res.urgency)
    }

    @Test
    fun testCalcDeloadRecommendation_boundary_signals() {
        // We can mock low compliance and high fatigue, but we don't need to overcomplicate.
        // Let's pass 50 compliance. It adds 1 signal.
        val res = AlgorithmEngine.calcDeloadRecommendation(emptyList(), 50)
        // Signals = 1 (due to compliance < 60)
        assertEquals("none", res.urgency)
    }

    @Test
    fun testCheckPersonalRecords_normal() {
        val tLog = listOf(
            RichTrainingSession(date = d(10), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(
                ExerciseLog(id = "bench", name = "Bench", muscleGroup = "chest", sets = listOf(ExerciseSet(weight = 100.0, reps = 5, rpe = 8, isWarmup = false, completed = true)))
            )),
            RichTrainingSession(date = d(0), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(
                ExerciseLog(id = "bench", name = "Bench", muscleGroup = "chest", sets = listOf(ExerciseSet(weight = 105.0, reps = 5, rpe = 8, isWarmup = false, completed = true)))
            ))
        )
        val res = AlgorithmEngine.checkPersonalRecords(tLog, "bench")
        assertTrue(res.hasPR)
        assertTrue(res.newPRs.any { it.type == "weight" })
    }

    @Test
    fun testCheckPersonalRecords_edgeCase_empty() {
        val res = AlgorithmEngine.checkPersonalRecords(emptyList(), "bench")
        assertFalse(res.hasPR)
    }
}
