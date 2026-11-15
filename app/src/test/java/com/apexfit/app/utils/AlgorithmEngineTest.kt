package com.apexfit.app.utils

import com.apexfit.app.data.RichTrainingSession
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar

class AlgorithmEngineTest {

    private fun d(daysAgo: Int): String = getDateDaysAgo(daysAgo)

    @Test
    fun trendWeightAdvancesAcrossCalendarGaps() {
        val log = listOf(
            WeightEntry(date = d(5), weight = 80.0),
            WeightEntry(date = d(0), weight = 82.0)
        )
        val trend = AlgorithmEngine.calcTrendWeight(log)
        val dailyAlpha = 2.0 / 15.0
        val effectiveAlpha = 1.0 - Math.pow(1.0 - dailyAlpha, 5.0)
        val expected = Math.round((80.0 + effectiveAlpha * 2.0) * 100.0) / 100.0

        assertEquals(expected, trend.last().trend, 0.0001)
    }

    @Test
    fun testCalcAdaptiveTDEE_normalInput() {
        val wLog = (0..14).map {
            WeightEntry(date = d(14 - it), weight = 80.0 - 0.1 * it)
        }
        val nLog = (0..14).map {
            NutritionEntry(date = d(it), calories = 2500, protein = 150, carbs = 250, fat = 80)
        }
        val res = TdeeEngine.calcAdaptiveTDEE(wLog, nLog, windowDays = 14)

        val cutoff = getWindowStartDate(14)
        val recentWeight = wLog.filter { it.date >= cutoff }.sortedBy { it.date }
        val avgCalories = nLog.filter { it.date >= cutoff }.map { it.calories }.average()
        val trendData = AlgorithmEngine.calcTrendWeight(wLog.sortedBy { it.date })
        val trendAtCutoff = trendData.first { it.date >= recentWeight.first().date }.trend
        val weightChangeKg = trendData.last().trend - trendAtCutoff
        val daysBetween = getDaysBetween(recentWeight.first().date, recentWeight.last().date).coerceAtLeast(1L)
        val expectedImbalance = (weightChangeKg * 7700.0) / daysBetween
        val expectedTdee = kotlin.math.round(avgCalories - expectedImbalance).toInt()

        assertEquals(expectedTdee, res.tdee)
    }

    @Test
    fun testCalcAdaptiveTDEE_edgeCase_empty() {
        val res = TdeeEngine.calcAdaptiveTDEE(emptyList(), emptyList())
        assertTrue((res.tdee ?: 0) > 0) // Should fallback to BMR
    }

    @Test
    fun testSuggestCaloricTarget_normal() {
        val res = TdeeEngine.suggestCaloricTarget(2500, "lose fat", 70.0, 65.0)
        // 70kg * 0.008 = 0.56 kg/week -> (0.56 * 7700) / 7 = 616 kcal/day deficit => 2500 - 616 = 1884
        assertEquals(1884, res)
    }

    @Test
    fun testSuggestCaloricTarget_recomposition() {
        val res = TdeeEngine.suggestCaloricTarget(2500, "recomposition", 70.0, 70.0)
        assertEquals(2500, res)
    }

    @Test
    fun testSuggestCaloricTarget_edgeCase_emptyGoal() {
        val res = TdeeEngine.suggestCaloricTarget(2500, "", 70.0, 65.0)
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
        val resPlateau = PlateauEngine.detectPlateau(wLog, nLog, listOf(earlierSession, recentSession), goal = "lose fat", windowDays = 14)
        assertTrue(resPlateau.isPlateaued)
        assertEquals(14, resPlateau.daysStalled)

        // Case 2: volume dropped >=5% (500 vs 1000) -> not a plateau
        val exSetLow = ExerciseSet(weight = 50.0, reps = 10, rpe = 8, isWarmup = false, completed = true)
        val exLogLow = ExerciseLog(id = "bench", name = "Bench", muscleGroup = "chest", sets = listOf(exSetLow))
        val recentSessionLow = RichTrainingSession(date = d(2), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(exLogLow))

        val resNoPlateau = PlateauEngine.detectPlateau(wLog, nLog, listOf(earlierSession, recentSessionLow), goal = "lose fat", windowDays = 14)
        assertFalse(resNoPlateau.isPlateaued)
    }

    @Test
    fun testDetectPlateau_edgeCase_empty() {
        val res = PlateauEngine.detectPlateau(emptyList(), emptyList(), emptyList(), goal = "lose fat", windowDays = 10)
        assertFalse(res.isPlateaued)
    }


    @Test
    fun testDetectPlateau_stableWeightIsNotPlateauForMaintenance() {
        val wLog = (0..14).map { WeightEntry(date = d(14 - it), weight = 80.0) }
        val nLog = (0..14).map { NutritionEntry(date = d(14 - it), calories = 2400, protein = 150, carbs = 250, fat = 70) }
        val set = ExerciseSet(weight = 100.0, reps = 10, rpe = 8, isWarmup = false, completed = true)
        val ex = ExerciseLog(id = "bench", name = "Bench", muscleGroup = "chest", sets = listOf(set))
        val sessions = listOf(
            RichTrainingSession(date = d(20), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(ex)),
            RichTrainingSession(date = d(2), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(ex))
        )

        val result = PlateauEngine.detectPlateau(wLog, nLog, sessions, goal = "maintain", windowDays = 14)
        assertFalse("Stable weight is expected at maintenance and must not trigger a cut-calories plateau", result.isPlateaued)
    }

    @Test
    fun testCalcFatigueToFitness_normal() {
        val tLog = (0..14).map { 
            val exSet = ExerciseSet(weight = 100.0, reps = 10, rpe = 8, isWarmup = false, completed = true)
            val exLog = ExerciseLog(id = "squat", name = "Squat", muscleGroup = "quads", sets = listOf(exSet))
            RichTrainingSession(date = d(14 - it), sessionType = "A", completed = true, durationMinutes = 60, sessionFeel = 3, exercises = listOf(exLog)) 
        }
        val res = FatigueEngine.calcFatigueToFitnessRatio(tLog)
        assertNotNull(res.ratio)
    }

    @Test
    fun testCalcFatigueToFitness_edgeCase_empty() {
        val res = FatigueEngine.calcFatigueToFitnessRatio(emptyList())
        assertNull(res.ratio)
    }

    @Test
    fun testCalcComplianceScores_normal() {
        val nLog = (0..13).map { NutritionEntry(date = d(13 - it), calories = 2500, protein = 150, carbs = 200, fat = 60) }
        val targets = NutritionTargets(2500, 150, 200, 60, 4)
        val res = ComplianceEngine.calcComplianceScores(nLog, emptyList(), targets, 14)
        assertEquals(100, res.protein)
        assertEquals(100, res.calories)
    }

    @Test
    fun testCalcComplianceScores_edgeCase_empty() {
        val res = ComplianceEngine.calcComplianceScores(emptyList(), emptyList(), NutritionTargets(2500, 150, 200, 60, 4), 14)
        assertEquals(0, res.overall)
    }

    @Test
    fun testCalcDeloadRecommendation_normal() {
        val res = PlateauEngine.calcDeloadRecommendation(emptyList(), 100)
        assertEquals("none", res.urgency)
    }

    @Test
    fun testCalcDeloadRecommendation_lowComplianceIsNotFatigueSignal() {
        val res = PlateauEngine.calcDeloadRecommendation(emptyList(), 50)
        assertEquals("none", res.urgency)
        assertEquals(0, res.signals)
    }

    @Test
    fun testCalcDeloadRecommendation_highLoadAloneDoesNotForceDeload() {
        val fatigue = FatigueResult(
            ratio = 1.7,
            status = "high",
            statusLabel = "Well Above Baseline",
            recommendation = "",
            acuteLoad = 170.0,
            chronicLoad = 100.0
        )
        val sessions = (0 until 4).map {
            RichTrainingSession(
                date = d(it),
                sessionType = "Upper",
                completed = true,
                sessionFeel = 4
            )
        }

        val res = PlateauEngine.calcDeloadRecommendation(sessions, 100, fatigue)
        assertEquals("none", res.urgency)
    }

    @Test
    fun testCalcDeloadRecommendation_highLoadAndPersistentPoorFeelCanDeload() {
        val fatigue = FatigueResult(
            ratio = 1.7,
            status = "high",
            statusLabel = "Well Above Baseline",
            recommendation = "",
            acuteLoad = 170.0,
            chronicLoad = 100.0
        )
        val sessions = (0 until 4).map {
            RichTrainingSession(
                date = d(it),
                sessionType = "Upper",
                completed = true,
                sessionFeel = 2
            )
        }

        val res = PlateauEngine.calcDeloadRecommendation(sessions, 100, fatigue)
        assertEquals("immediate", res.urgency)
        assertEquals(2, res.signals)
    }

    @Test
    fun testEstimateLBM() {
        val maleLBM = GoalEngine.estimateLBM(80.0, 180.0, "male")
        assertEquals(61.42, maleLBM, 0.01)

        val femaleLBM = GoalEngine.estimateLBM(60.0, 165.0, "female")
        assertEquals(44.865, femaleLBM, 0.01)
    }

    @Test
    fun testCalcMacroTargets() {
        val targets = MacroEngine.calcMacroTargets(
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
        val cycled = MacroEngine.calcCycledTargets(
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
    fun goalTimeline_contradictoryGainTargetDoesNotInventGainSchedule() {
        val timeline = GoalEngine.calcGoalTimeline(
            currentWeightKg = 80.0,
            goalWeightKg = 75.0,
            resolvedGoal = "Gain Muscle",
            heightCm = 180.0,
            sex = "male"
        )

        assertEquals(0, timeline.weeksToGoal)
        assertEquals(0.0, timeline.weeklyChangeKg, 0.001)
        assertEquals(80.0, timeline.adjustedGoalWeightKg, 0.001)
    }

    @Test
    fun goalTimeline_contradictoryLossTargetDoesNotInventCutSchedule() {
        val timeline = GoalEngine.calcGoalTimeline(
            currentWeightKg = 80.0,
            goalWeightKg = 85.0,
            resolvedGoal = "Lose Fat",
            heightCm = 180.0,
            sex = "male"
        )

        assertEquals(0, timeline.weeksToGoal)
        assertEquals(0.0, timeline.weeklyChangeKg, 0.001)
        assertEquals(80.0, timeline.adjustedGoalWeightKg, 0.001)
    }

    @Test
    fun testCalcGoalTimeline_realisticFatLoss() {
        val timeline = GoalEngine.calcGoalTimeline(
            currentWeightKg = 80.0,
            goalWeightKg = 76.0,
            resolvedGoal = "Lose Fat",
            heightCm = 175.0,
            sex = "male"
        )
        assertEquals(7, timeline.weeksToGoal)
        assertEquals(0.64, timeline.weeklyChangeKg, 0.001)
        assertTrue(timeline.isRealistic)
        assertEquals(76.0, timeline.adjustedGoalWeightKg, 0.001)
        assertTrue(timeline.summaryLine.contains("~7 weeks at 0.64 kg/week"))
    }

    @Test
    fun testCalcGoalTimeline_unrealisticFatLoss() {
        val timeline = GoalEngine.calcGoalTimeline(
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
        val timeline = GoalEngine.calcGoalTimeline(
            currentWeightKg = 75.0,
            goalWeightKg = 75.0,
            resolvedGoal = "Recomposition",
            heightCm = 170.0,
            sex = "female"
        )
        assertTrue(timeline.isRealistic)
        assertEquals(0.0, timeline.weeklyChangeKg, 0.001)
        assertTrue(timeline.summaryLine.contains("Body recomposition"))
    }

    @Test
    fun testCalcAdaptiveTDEE_warmStartedEMA_losingAndGaining() {
        // 28-day history losing 0.5 kg per 14 days (1.0 kg over 28 days, i.e. 1.0 / 28 kg/day)
        val wLogLoss = (0..28).map { day ->
            WeightEntry(date = d(28 - day), weight = 80.0 - (1.0 / 28.0) * day)
        }
        val nLog = (0..28).map { day ->
            NutritionEntry(date = d(day), calories = 2500, protein = 150, carbs = 250, fat = 80)
        }
        val resLoss = TdeeEngine.calcAdaptiveTDEE(wLogLoss, nLog, windowDays = 14)
        assertNotNull(resLoss.tdee)
        assertTrue("TDEE while losing should be within ±30 kcal of 2775, got ${resLoss.tdee}", Math.abs(resLoss.tdee!! - 2775) <= 30)

        // 28-day history gaining 0.5 kg per 14 days (1.0 kg over 28 days)
        val wLogGain = (0..28).map { day ->
            WeightEntry(date = d(28 - day), weight = 80.0 + (1.0 / 28.0) * day)
        }
        val resGain = TdeeEngine.calcAdaptiveTDEE(wLogGain, nLog, windowDays = 14)
        assertNotNull(resGain.tdee)
        assertTrue("TDEE while gaining should be within ±30 kcal of 2225, got ${resGain.tdee}", Math.abs(resGain.tdee!! - 2225) <= 30)
    }
}
