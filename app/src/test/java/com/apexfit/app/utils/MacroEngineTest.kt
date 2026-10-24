package com.apexfit.app.utils

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class MacroEngineTest {

    // ── Helpers ───────────────────────────────────────────────

    // Tolerance for calorie balance checks (rounding can introduce ±~10 kcal)
    private val CAL_TOLERANCE = 12

    private fun calBalance(r: com.apexfit.app.data.NutritionTargets): Int =
        r.protein * 4 + r.carbs * 4 + r.fat * 9

    // ── Protein targets ───────────────────────────────────────

    @Test
    fun gainMuscle_protein_derivedFromLBMAt2gPerKg() {
        // Male 80kg, 175cm: LBM = (0.407×80) + (0.267×175) - 19.2 = 60.085
        // gain muscle multiplier = 2.0 → protein = round(60.085 × 2.0) = 120g
        val result = MacroEngine.calcMacroTargets(
            calorieTarget = 2800,
            bodyWeightKg = 80.0,
            goal = "gain muscle",
            heightCm = 175.0,
            sex = "male"
        )
        assertEquals(120, result.protein)
    }

    @Test
    fun loseFat_protein_derivedFromLBMAt2pt4gPerKg() {
        // Female 60kg, 165cm: LBM = (0.252×60) + (0.473×165) - 48.3 = 44.865
        // lose fat multiplier = 2.4 → protein = round(44.865 × 2.4) = round(107.676) = 108g
        val result = MacroEngine.calcMacroTargets(
            calorieTarget = 1600,
            bodyWeightKg = 60.0,
            goal = "lose fat",
            heightCm = 165.0,
            sex = "female"
        )
        assertEquals(108, result.protein)
    }

    @Test
    fun recomposition_protein_highestMultiplier() {
        // Recomposition = 2.8 g/kg LBM — highest of all goals
        val recomposition = MacroEngine.calcMacroTargets(2500, 75.0, "recomposition", 175.0, "male")
        val gainMuscle   = MacroEngine.calcMacroTargets(2500, 75.0, "gain muscle",   175.0, "male")
        assertTrue("Recomp protein ${recomposition.protein} should be >= gain muscle ${gainMuscle.protein}",
            recomposition.protein >= gainMuscle.protein)
    }

    @Test
    fun protein_alwaysAtLeast100g() {
        // Very light person with low calorie goal — protein floor must hold
        val result = MacroEngine.calcMacroTargets(
            calorieTarget = 1400,
            bodyWeightKg = 40.0,
            goal = "maintain",
            heightCm = 155.0,
            sex = "female"
        )
        assertTrue("Protein ${result.protein} should be >= 100", result.protein >= 100)
    }

    @Test
    fun protein_neverExceeds350g() {
        // Very large person, high multiplier goal
        val result = MacroEngine.calcMacroTargets(
            calorieTarget = 5000,
            bodyWeightKg = 150.0,
            goal = "recomposition",
            heightCm = 195.0,
            sex = "male"
        )
        assertTrue("Protein ${result.protein} should be <= 350", result.protein <= 350)
    }

    // ── Fat targets ───────────────────────────────────────────

    @Test
    fun fat_gainMuscle_is1gPerKgBodyweight() {
        // gain muscle → fat = round(bodyWeight × 1.0)
        val result = MacroEngine.calcMacroTargets(2800, 80.0, "gain muscle", 175.0, "male")
        assertEquals(80, result.fat)
    }

    @Test
    fun fat_loseFat_is0pt8gPerKgBodyweight() {
        // lose fat → fat = round(bodyWeight × 0.8)
        val result = MacroEngine.calcMacroTargets(2000, 80.0, "lose fat", 175.0, "male")
        assertEquals(64, result.fat)
    }

    @Test
    fun fat_alwaysAtLeast40g() {
        // Very light person — fat floor must hold
        val result = MacroEngine.calcMacroTargets(1200, 40.0, "lose fat", 155.0, "female")
        assertTrue("Fat ${result.fat} should be >= 40", result.fat >= 40)
    }

    // ── Calorie balance ───────────────────────────────────────

    @Test
    fun macros_calorieBalance_matchesTarget_gainMuscle() {
        // protein×4 + carbs×4 + fat×9 must be within tolerance of calorieTarget
        val target = 2800
        val result = MacroEngine.calcMacroTargets(target, 80.0, "gain muscle", 175.0, "male")
        val balance = calBalance(result)
        assertTrue("Cal balance $balance should be within $CAL_TOLERANCE of target $target",
            abs(balance - target) <= CAL_TOLERANCE)
    }

    @Test
    fun macros_calorieBalance_matchesTarget_loseFat() {
        val target = 1800
        val result = MacroEngine.calcMacroTargets(target, 70.0, "lose fat", 168.0, "female")
        val balance = calBalance(result)
        assertTrue("Cal balance $balance should be within $CAL_TOLERANCE of target $target",
            abs(balance - target) <= CAL_TOLERANCE)
    }

    @Test
    fun macros_calorieBalance_matchesTarget_recomposition() {
        val target = 2400
        val result = MacroEngine.calcMacroTargets(target, 75.0, "recomposition", 172.0, "male")
        val balance = calBalance(result)
        assertTrue("Cal balance $balance should be within $CAL_TOLERANCE of target $target",
            abs(balance - target) <= CAL_TOLERANCE)
    }

    // ── Carbs ─────────────────────────────────────────────────

    @Test
    fun carbs_neverNegative() {
        // Even when protein + fat consume most calories, carbs must be >= 0
        val result = MacroEngine.calcMacroTargets(
            calorieTarget = 1000,
            bodyWeightKg = 80.0,
            goal = "lose fat",
            heightCm = 175.0,
            sex = "male"
        )
        assertTrue("Carbs ${result.carbs} should be >= 0", result.carbs >= 0)
    }

    @Test
    fun carbs_neverExceeds600g() {
        val result = MacroEngine.calcMacroTargets(5000, 100.0, "gain muscle", 185.0, "male")
        assertTrue("Carbs ${result.carbs} should be <= 600", result.carbs <= 600)
    }

    // ── Oversubscribed calories ───────────────────────────────

    @Test
    fun oversubscribed_proteinStillInBounds() {
        // When calories are very low relative to protein+fat needs, scaling kicks in
        // Protein must still be clamped to [100, 350]
        val result = MacroEngine.calcMacroTargets(
            calorieTarget = 900,
            bodyWeightKg = 90.0,
            goal = "lose fat",
            heightCm = 180.0,
            sex = "male"
        )
        assertTrue("Protein ${result.protein} should be in [100, 350]",
            result.protein in 100..350)
        assertTrue("Carbs ${result.carbs} should be >= 0", result.carbs >= 0)
    }

    // ── Cycling ───────────────────────────────────────────────

    @Test
    fun calcCycledTargets_trainingDayCals_higherThanRestDay() {
        val result = MacroEngine.calcCycledTargets(
            weeklyCalorieTarget = 2500,
            weeklyTrainingSessions = 4,
            goal = "gain muscle",
            bodyWeightKg = 80.0,
            heightCm = 175.0,
            sex = "male"
        )
        assertTrue("Training day ${result.trainingDayCalories} should be > rest day ${result.restDayCalories}",
            result.trainingDayCalories > result.restDayCalories)
    }

    @Test
    fun calcCycledTargets_restDayCals_neverBelow800() {
        // The engine has a safety loop to prevent rest days below 800 kcal
        val result = MacroEngine.calcCycledTargets(
            weeklyCalorieTarget = 1500,
            weeklyTrainingSessions = 5,
            goal = "lose fat",
            bodyWeightKg = 60.0,
            heightCm = 165.0,
            sex = "female"
        )
        assertTrue("Rest day calories ${result.restDayCalories} should be >= 800",
            result.restDayCalories >= 800)
    }

    @Test
    fun calcCycledTargets_trainingProtein_higherThanRestProtein() {
        val result = MacroEngine.calcCycledTargets(
            weeklyCalorieTarget = 2500,
            weeklyTrainingSessions = 4,
            goal = "gain muscle",
            bodyWeightKg = 80.0,
            heightCm = 175.0,
            sex = "male"
        )
        assertTrue("Training protein ${result.trainingDayProtein} >= rest protein ${result.restDayProtein}",
            result.trainingDayProtein >= result.restDayProtein)
    }
}
