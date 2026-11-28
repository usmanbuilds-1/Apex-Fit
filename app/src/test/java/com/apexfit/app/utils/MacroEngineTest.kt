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
    fun gainMuscle_proteinUsesBodyWeight() {
        // 80 kg × 1.8 g/kg = 144 g.
        val result = MacroEngine.calcMacroTargets(
            calorieTarget = 2800,
            bodyWeightKg = 80.0,
            goal = "gain muscle",
            heightCm = 175.0,
            sex = "male"
        )
        assertEquals(144, result.protein)
    }

    @Test
    fun loseFat_proteinUsesBodyWeight() {
        // 60 kg × 2.2 g/kg = 132 g.
        val result = MacroEngine.calcMacroTargets(
            calorieTarget = 1600,
            bodyWeightKg = 60.0,
            goal = "lose fat",
            heightCm = 165.0,
            sex = "female"
        )
        assertEquals(132, result.protein)
    }

    @Test
    fun recomposition_proteinAtLeastGainMuscle() {
        val recomposition = MacroEngine.calcMacroTargets(2500, 75.0, "recomposition", 175.0, "male")
        val gainMuscle   = MacroEngine.calcMacroTargets(2500, 75.0, "gain muscle",   175.0, "male")
        assertTrue("Recomp protein ${recomposition.protein} should be >= gain muscle ${gainMuscle.protein}",
            recomposition.protein >= gainMuscle.protein)
    }

    @Test
    fun lighterUserDoesNotGetArbitrary100gProteinFloor() {
        val result = MacroEngine.calcMacroTargets(
            calorieTarget = 1400,
            bodyWeightKg = 40.0,
            goal = "maintain",
            heightCm = 155.0,
            sex = "female"
        )
        assertEquals(64, result.protein)
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
    fun fatScalesWithWeightAndEnergy() {
        val result = MacroEngine.calcMacroTargets(1200, 40.0, "lose fat", 155.0, "female")
        assertEquals(32, result.fat)
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
    fun highCalorieTarget_remainsConsistentWithMacros() {
        val result = MacroEngine.calcMacroTargets(5000, 100.0, "gain muscle", 185.0, "male")
        assertTrue(abs(calBalance(result) - 5000) <= CAL_TOLERANCE)
    }

    // ── Oversubscribed calories ───────────────────────────────

    @Test
    fun oversubscribed_macrosStayWithinCalorieBudget() {
        val result = MacroEngine.calcMacroTargets(900, 90.0, "lose fat", 180.0, "male")
        assertTrue(abs(calBalance(result) - 900) <= CAL_TOLERANCE)
        assertTrue(result.protein >= 0 && result.fat >= 0 && result.carbs >= 0)
    }

    @Test
    fun cycledTargets_matchEachDaysCaloriesWhenBudgetIsTight() {
        val result = MacroEngine.calcCycledTargets(1000, 5, "lose fat", 100.0, 180.0, "male")
        val train = result.trainingDayProtein * 4 + result.trainingDayCarbs * 4 + result.fat * 9
        val rest = result.restDayProtein * 4 + result.restDayCarbs * 4 + result.fat * 9
        assertTrue(abs(train - result.trainingDayCalories) <= CAL_TOLERANCE)
        assertTrue(abs(rest - result.restDayCalories) <= CAL_TOLERANCE)
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
    fun calcCycledTargets_restDayCalsPreserveGuardrails() {
        val result = MacroEngine.calcCycledTargets(
            weeklyCalorieTarget = 1500,
            weeklyTrainingSessions = 5,
            goal = "lose fat",
            bodyWeightKg = 60.0,
            heightCm = 165.0,
            sex = "female"
        )
        assertTrue(result.restDayCalories >= 1350)
    }

    @Test
    fun calcCycledTargets_proteinEqualAcrossDays() {
        val result = MacroEngine.calcCycledTargets(
            weeklyCalorieTarget = 2500,
            weeklyTrainingSessions = 4,
            goal = "gain muscle",
            bodyWeightKg = 80.0,
            heightCm = 175.0,
            sex = "male"
        )
        assertEquals(result.trainingDayProtein, result.restDayProtein)
    }

    @Test
    fun calcMacroTargets_preservesUserTrainingFrequency() {
        val result = MacroEngine.calcMacroTargets(
            calorieTarget = 2400,
            bodyWeightKg = 80.0,
            goal = "lose fat",
            heightCm = 180.0,
            sex = "male",
            weeklyTrainingSessions = 2
        )

        assertEquals(
            "A fat-loss goal must not silently turn a two-day training plan into a five-day compliance target",
            2,
            result.weeklyTrainingSessions
        )
    }

    @Test
    fun cyclingPreservesWeeklyEnergyAndGuardrailsAcrossAllFrequencies() {
        for (days in 0..7) for (base in listOf(800, 1200, 1500, 2500, 4000)) {
            val targets = MacroEngine.calcCycledTargets(base, days, "gain muscle", 80.0, 175.0, "male")
            assertTrue(abs(targets.trainingDayCalories * days + targets.restDayCalories * (7 - days) - base * 7) <= 3)
            assertTrue(targets.restDayCalories >= minOf(base, maxOf(1200, kotlin.math.ceil(base * 0.90).toInt())))
            assertEquals(targets.trainingDayProtein, targets.restDayProtein)
            assertTrue(abs(targets.trainingDayProtein * 4 + targets.trainingDayCarbs * 4 + targets.fat * 9 - targets.trainingDayCalories) <= 2)
            assertTrue(abs(targets.restDayProtein * 4 + targets.restDayCarbs * 4 + targets.fat * 9 - targets.restDayCalories) <= 2)
            if (days == 0 || days == 7 || base < 1200) {
                assertEquals(base, targets.trainingDayCalories)
                assertEquals(base, targets.restDayCalories)
            }
        }
    }

    @Test
    fun restrictedBudgetIsFlaggedAndBuildMuscleAliasMatches() {
        assertTrue(MacroEngine.hasRestrictedBudget(900, 90.0, "lose fat"))
        assertFalse(MacroEngine.hasRestrictedBudget(2500, 80.0, "gain muscle"))
        assertEquals(MacroEngine.calcMacroTargets(2500, 80.0, "gain muscle"),
            MacroEngine.calcMacroTargets(2500, 80.0, "Build Muscle"))
    }

}
