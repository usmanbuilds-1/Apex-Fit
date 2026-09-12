package com.apexfit.app.utils

import com.apexfit.app.data.RichTrainingSession
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
        val res = AlgorithmEngine.suggestCaloricTarget(2500, "lose fat", 70.0, 65.0)
        // 70kg * 0.008 = 0.56 kg/week -> (0.56 * 7700) / 7 = 616 kcal/day deficit => 2500 - 616 = 1884
        assertEquals(1884, res)
    }

    @Test
    fun testSuggestCaloricTarget_recomposition() {
        val res = AlgorithmEngine.suggestCaloricTarget(2500, "recomposition", 70.0, 70.0)
        assertEquals(2500, res)
    }

    @Test
    fun testSuggestCaloricTarget_edgeCase_emptyGoal() {
        val res = AlgorithmEngine.suggestCaloricTarget(2500, "", 70.0, 65.0)
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
        assertTrue(resPlateau.isPlateaued)
        assertEquals(14, resPlateau.daysStalled)

        // Case 2: volume dropped >=5% (500 vs 1000) -> not a plateau
        val exSetLow = ExerciseSet(weight = 50.0, reps = 10, rpe = 8, isWarmup = false, completed = true)
        val exLogLow = ExerciseLog(id = "bench", name = "Bench", muscleGroup = "chest", sets = listOf(exSetLow))
        val recentSessionLow = RichTrainingSession(date = d(2), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(exLogLow))

        val resNoPlateau = AlgorithmEngine.detectPlateau(wLog, nLog, listOf(earlierSession, recentSessionLow), windowDays = 14)
        assertFalse(resNoPlateau.isPlateaued)
    }

    @Test
    fun testDetectPlateau_edgeCase_empty() {
        val res = AlgorithmEngine.detectPlateau(emptyList(), emptyList(), emptyList(), windowDays = 10)
        assertFalse(res.isPlateaued)
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
    fun testEstimateLBM() {
        val maleLBM = AlgorithmEngine.estimateLBM(80.0, 180.0, "male")
        assertEquals(61.42, maleLBM, 0.01)

        val femaleLBM = AlgorithmEngine.estimateLBM(60.0, 165.0, "female")
        assertEquals(44.865, femaleLBM, 0.01)
    }

    @Test
    fun testCalcMacroTargets() {
        val targets = AlgorithmEngine.calcMacroTargets(
            calorieTarget = 2400,
            bodyWeightKg  = 80.0,
            goal          = "gain muscle",
            heightCm      = 180.0,
            sex           = "male"
        )
        assertEquals(2400, targets.calories)
        assertEquals(123, targets.protein)
        assertEquals(80, targets.fat)
        assertEquals(297, targets.carbs)
    }

    @Test
    fun testCalcCycledTargets() {
        val cycled = AlgorithmEngine.calcCycledTargets(
            weeklyCalorieTarget = 2500,
            weeklyTrainingSessions = 4,
            goal = "gain muscle",
            bodyWeightKg = 80.0,
            heightCm = 180.0,
            sex = "male"
        )
        assertEquals(2700, cycled.trainingDayCalories)
        assertEquals(2233, cycled.restDayCalories)
        assertEquals(135, cycled.trainingDayProtein)
        assertEquals(111, cycled.restDayProtein)
        assertEquals(72, cycled.fat)
        assertTrue(cycled.trainingDayCarbs > cycled.restDayCarbs)
    }

    @Test
    fun testCalcGoalTimeline_realisticFatLoss() {
        val timeline = AlgorithmEngine.calcGoalTimeline(
            currentWeightKg = 80.0,
            goalWeightKg = 76.0,
            resolvedGoal = "Lose Fat",
            heightCm = 175.0,
            sex = "male"
        )
        assertEquals(6, timeline.weeksToGoal)
        assertEquals(0.64, timeline.weeklyChangeKg, 0.001)
        assertTrue(timeline.isRealistic)
        assertEquals(76.0, timeline.adjustedGoalWeightKg, 0.001)
        assertTrue(timeline.summaryLine.contains("~6 weeks at 0.64 kg/week"))
    }

    @Test
    fun testCalcGoalTimeline_unrealisticFatLoss() {
        val timeline = AlgorithmEngine.calcGoalTimeline(
            currentWeightKg = 150.0,
            goalWeightKg = 60.0,
            resolvedGoal = "Lose Fat",
            heightCm = 175.0,
            sex = "male"
        )
        assertFalse(timeline.isRealistic)
        assertEquals(104, timeline.weeksToGoal)
        assertEquals(113.6, timeline.adjustedGoalWeightKg, 0.001)
        assertTrue(timeline.summaryLine.contains("over 2 years"))
    }

    @Test
    fun testCalcGoalTimeline_recomposition() {
        val timeline = AlgorithmEngine.calcGoalTimeline(
            currentWeightKg = 75.0,
            goalWeightKg = 75.0,
            resolvedGoal = "Recomposition",
            heightCm = 170.0,
            sex = "female"
        )
        assertTrue(timeline.isRealistic)
        assertEquals(0.05, timeline.weeklyChangeKg, 0.001)
        assertTrue(timeline.summaryLine.contains("Body recomposition"))
    }
}
