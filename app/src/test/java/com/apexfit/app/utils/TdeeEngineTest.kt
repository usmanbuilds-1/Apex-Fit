package com.apexfit.app.utils

import com.apexfit.app.data.NutritionEntry
import com.apexfit.app.data.WeightEntry
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class TdeeEngineTest {

    // ── Helpers ───────────────────────────────────────────────

    // Single weight entry: enough to set weight, not enough to trigger adaptive path
    private fun oneWeight(kg: Double) = listOf(WeightEntry(date = "2020-01-01", weight = kg))

    private fun weightLog(days: Int, weight: Double): List<WeightEntry> =
        (0 until days).map { WeightEntry(date = getDateDaysAgo(days - 1 - it), weight = weight) }

    private fun nutritionLog(days: Int, calories: Int): List<NutritionEntry> =
        (0 until days).map {
            NutritionEntry(
                date = getDateDaysAgo(days - 1 - it),
                calories = calories, protein = 150, carbs = 200, fat = 60
            )
        }

    // ── Mifflin-St Jeor fallback ──────────────────────────────
    //
    // Formula (Mifflin MD, et al. 1990):
    //   BMR = (10 × kg) + (6.25 × cm) - (5 × age) + sexOffset
    //   male sexOffset = +5, female = -161
    //
    // Activity multipliers:
    //   0–1 workouts/week  → 1.200
    //   2–3 workouts/week  → 1.375
    //   4–5 workouts/week  → 1.550
    //   6+  workouts/week  → 1.725

    @Test
    fun fallback_maleProfile_returnsCorrectMifflinTDEE() {
        // weight 75kg (from oneWeight), height 175cm, age 25, male, 4 workouts
        // BMR = (10×75) + (6.25×175) - (5×25) + 5 = 1723.75
        // TDEE = 1723.75 × 1.55 = 2671.8 → 2672
        val result = TdeeEngine.calcAdaptiveTDEE(
            weightLog = oneWeight(75.0),
            nutritionLog = emptyList(),
            heightCm = 175.0, ageYears = 25,
            biologicalSex = "male", weeklyWorkouts = 4
        )
        assertEquals(2672, result.tdee)
        assertTrue(result.confidence.contains("Mifflin", ignoreCase = true))
    }

    @Test
    fun fallback_femaleProfile_appliesCorrectSexOffset() {
        // weight 60kg, height 165cm, age 30, female, 3 workouts
        // BMR = (10×60) + (6.25×165) - (5×30) - 161 = 1320.25
        // TDEE = 1320.25 × 1.375 = 1815.34 → 1815
        val result = TdeeEngine.calcAdaptiveTDEE(
            weightLog = oneWeight(60.0),
            nutritionLog = emptyList(),
            heightCm = 165.0, ageYears = 30,
            biologicalSex = "female", weeklyWorkouts = 3
        )
        assertEquals(1815, result.tdee)
    }

    @Test
    fun fallback_femaleVsMale_sameInputs_femaleIsLower() {
        // Female sex offset (-161) vs male (+5) → female TDEE always lower at equal inputs
        val male = TdeeEngine.calcAdaptiveTDEE(
            oneWeight(75.0), emptyList(), 175.0, 30, "male", 3
        )
        val female = TdeeEngine.calcAdaptiveTDEE(
            oneWeight(75.0), emptyList(), 175.0, 30, "female", 3
        )
        assertTrue("Female TDEE ${female.tdee} should be < male TDEE ${male.tdee}",
            female.tdee!! < male.tdee!!)
        // Difference should equal the offset difference: (5 - (-161)) × 1.375 = 166 × 1.375 = 228.25 → 228
        val diff = male.tdee!! - female.tdee!!
        assertEquals(228, diff, 2) // ±2 for rounding
    }

    @Test
    fun fallback_sedentaryMultiplier_appliesCorrectly() {
        // weight 80kg, height 170cm, age 35, male, 0 workouts → ×1.2
        val weight = 80.0
        val result = TdeeEngine.calcAdaptiveTDEE(
            oneWeight(weight), emptyList(), 170.0, 35, "male", 0
        )
        val bmr = (10.0 * weight) + (6.25 * 170.0) - (5.0 * 35) + 5.0
        assertEquals((bmr * 1.2).roundToInt(), result.tdee)
    }

    @Test
    fun fallback_lightlyActiveMultiplier_appliesCorrectly() {
        val weight = 80.0
        val result = TdeeEngine.calcAdaptiveTDEE(
            oneWeight(weight), emptyList(), 170.0, 35, "male", 2
        )
        val bmr = (10.0 * weight) + (6.25 * 170.0) - (5.0 * 35) + 5.0
        assertEquals((bmr * 1.375).roundToInt(), result.tdee)
    }

    @Test
    fun fallback_veryActiveMultiplier_appliesCorrectly() {
        // 6 workouts/week → ×1.725
        val weight = 80.0
        val result = TdeeEngine.calcAdaptiveTDEE(
            oneWeight(weight), emptyList(), 170.0, 35, "male", 6
        )
        val bmr = (10.0 * weight) + (6.25 * 170.0) - (5.0 * 35) + 5.0
        assertEquals((bmr * 1.725).roundToInt(), result.tdee)
    }

    // ── Adaptive path ─────────────────────────────────────────

    @Test
    fun adaptive_stableWeightConstantIntake_tdeeApproximatesIntake() {
        // Stable weight → weightChangeKg ≈ 0 → TDEE ≈ avgCalories
        val result = TdeeEngine.calcAdaptiveTDEE(
            weightLog = weightLog(15, 80.0),
            nutritionLog = nutritionLog(15, 2500),
            heightCm = 175.0, ageYears = 25,
            biologicalSex = "male", weeklyWorkouts = 4
        )
        assertTrue("TDEE ${result.tdee} should be near 2500 when weight is stable",
            result.tdee!! in 2200..2800)
    }

    @Test
    fun adaptive_sufficientData_returnsHighOrMediumConfidence() {
        val result = TdeeEngine.calcAdaptiveTDEE(
            weightLog = weightLog(15, 80.0),
            nutritionLog = nutritionLog(15, 2500),
            heightCm = 175.0, ageYears = 25,
            biologicalSex = "male", weeklyWorkouts = 4
        )
        assertTrue("Expected high or medium confidence, got: ${result.confidence}",
            result.confidence == "high" || result.confidence == "medium")
    }

    @Test
    fun adaptive_insufficientWeightData_fallsBackToMifflin() {
        // Only 3 weight entries — below the 7-entry minimum
        val result = TdeeEngine.calcAdaptiveTDEE(
            weightLog = weightLog(3, 80.0),
            nutritionLog = nutritionLog(15, 2500),
            heightCm = 175.0, ageYears = 25,
            biologicalSex = "male", weeklyWorkouts = 4
        )
        assertTrue("Should fall back to Mifflin with 3 weight entries",
            result.confidence.contains("Mifflin", ignoreCase = true))
    }

    @Test
    fun adaptive_insufficientNutritionData_fallsBackToMifflin() {
        // Only 4 nutrition days — below the 7-day minimum
        val result = TdeeEngine.calcAdaptiveTDEE(
            weightLog = weightLog(15, 80.0),
            nutritionLog = nutritionLog(4, 2500),
            heightCm = 175.0, ageYears = 25,
            biologicalSex = "male", weeklyWorkouts = 4
        )
        assertTrue("Should fall back to Mifflin with 4 nutrition entries",
            result.confidence.contains("Mifflin", ignoreCase = true))
    }

    // ── Caloric target suggestions ────────────────────────────

    @Test
    fun suggestTarget_gainMuscle_returnsAboveTDEE() {
        val target = TdeeEngine.suggestCaloricTarget(
            tdee = 2500, goal = "gain muscle",
            currentWeightKg = 80.0, goalWeightKg = 85.0,
            heightCm = 175.0, ageYears = 25, sex = "male"
        )
        assertTrue("Gain target $target should be above TDEE 2500", target > 2500)
    }

    @Test
    fun suggestTarget_loseFat_returnsBelowTDEE() {
        val target = TdeeEngine.suggestCaloricTarget(
            tdee = 2500, goal = "lose fat",
            currentWeightKg = 80.0, goalWeightKg = 72.0,
            heightCm = 175.0, ageYears = 25, sex = "male"
        )
        assertTrue("Cut target $target should be below TDEE 2500", target < 2500)
    }

    @Test
    fun suggestTarget_recomposition_equalsTDEE() {
        val target = TdeeEngine.suggestCaloricTarget(
            tdee = 2500, goal = "recomposition",
            currentWeightKg = 80.0, goalWeightKg = 80.0,
            heightCm = 175.0, ageYears = 25, sex = "male"
        )
        assertEquals(2500, target)
    }

    @Test
    fun suggestTarget_neverBelowBmrFloor() {
        // Extreme deficit: very low TDEE — must not go below max(1200, BMR)
        val target = TdeeEngine.suggestCaloricTarget(
            tdee = 1000, goal = "lose fat",
            currentWeightKg = 45.0, goalWeightKg = 40.0,
            heightCm = 155.0, ageYears = 45, sex = "female"
        )
        assertTrue("Target $target should be at least 1200", target >= 1200)
    }

    @Test
    fun suggestTarget_gainRate_clampedForVeryLightPerson() {
        // 30kg person: rate = 30 × 0.004 = 0.12 → clamped to 0.15 kg/week minimum
        // daily surplus = (0.15 × 7700 / 7) = 165 kcal
        val target = TdeeEngine.suggestCaloricTarget(
            tdee = 1800, goal = "gain muscle",
            currentWeightKg = 30.0, goalWeightKg = 35.0,
            heightCm = 160.0, ageYears = 20, sex = "female"
        )
        assertTrue("Light person gain target $target should be above TDEE 1800", target > 1800)
    }

    @Test
    fun suggestTarget_lossRate_clampedForVeryHeavyPerson() {
        // 200kg person: rate = 200 × 0.008 = 1.6 → clamped to 0.70 kg/week maximum
        val target = TdeeEngine.suggestCaloricTarget(
            tdee = 3500, goal = "lose fat",
            currentWeightKg = 200.0, goalWeightKg = 180.0,
            heightCm = 185.0, ageYears = 35, sex = "male"
        )
        assertTrue("Heavy person cut target $target should be below TDEE 3500", target < 3500)
        // But not too aggressive: max rate is 0.70 kg/week = 770 kcal/day
        assertTrue("Cut $target should not exceed 770 kcal below TDEE", target >= 3500 - 780)
    }
}
