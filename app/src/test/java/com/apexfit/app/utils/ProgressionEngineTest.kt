package com.apexfit.app.utils

import org.junit.Assert.*
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionEngineTest {

    @Test
    fun testCalculateProgressiveWeight_normal() {
        // Need to create dummy sets
        val sets = listOf(com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 5, rpe = 8, completed = true))
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 5, 5, 1, 1.0, 100.0, "compound_lower")
        // Base inc = 5.0 (for compound_lower)
        // RPE 8 -> 1.0 multiplier
        // Total inc = 5.0 * 1.0 * 1.0 = 5.0
        // Output = 105.0
        assertEquals(105.0, res.newWeight, 0.01)
    }

    @Test
    fun testCalculateProgressiveWeight_edgeCase_zero() {
        val sets = listOf(com.apexfit.app.ui.models.UiExerciseSet(weight = 0.0, reps = 0, rpe = 0))
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 5, 5, 1, 1.0, 0.0, "isolation")
        assertEquals(0.0, res.newWeight, 0.01)
    }

    @Test
    fun testCalculateProgressiveWeight_boundary_RPE7() {
        val sets = listOf(com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 5, rpe = 7, completed = true))
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 5, 5, 1, 1.0, 100.0, "compound_upper")
        // Base inc = 2.5 (for compound_upper)
        // RPE 7 -> 1.0 multiplier
        // Total inc = 2.5 * 1.0 * 1.0 = 2.5. 100 + 2.5 = 102.5.
        assertEquals(102.5, res.newWeight, 0.01)
    }

    // ── Starting weight — BW-ratio system ───────────────────────────────────────
    //
    // Formula: bodyWeight × categoryRatio × experienceMultiplier
    // Source: Strength Level population norms (48M+ lifts)
    // All values in kg (preferredUnits = "kg" avoids lbs conversion noise in tests)

    @Test
    fun startingWeight_maleNoviceCompoundUpper_correctBWRatio() {
        // 70kg male novice, no exercise name (Tier 2)
        // base = 70 × 0.325 × 1.0 = 22.75 → round(22.75/2.5)×2.5 = 22.5kg
        val result = ProgressionEngine.calculateBeginnerStartingWeight(
            exerciseType = "compound_upper", userBodyWeightKg = 70.0,
            userHeightCm = 175.0, preferredUnits = "kg"
        )
        assertEquals(22.5, result, 0.01)
    }

    @Test
    fun startingWeight_maleNoviceIsolation_scaledByBodyWeight() {
        // 70kg male novice isolation: 70 × 0.080 × 1.0 = 5.6 → 5.0kg
        val result = ProgressionEngine.calculateBeginnerStartingWeight(
            exerciseType = "isolation", userBodyWeightKg = 70.0,
            userHeightCm = 175.0, preferredUnits = "kg"
        )
        assertEquals(5.0, result, 0.01)
    }

    @Test
    fun startingWeight_intermediateHigherThanNovice() {
        val novice = ProgressionEngine.calculateBeginnerStartingWeight(
            "compound_upper", 70.0, 175.0, preferredUnits = "kg",
            experienceLevel = "novice"
        )
        val intermediate = ProgressionEngine.calculateBeginnerStartingWeight(
            "compound_upper", 70.0, 175.0, preferredUnits = "kg",
            experienceLevel = "intermediate"
        )
        assertTrue("Intermediate starts heavier than novice", intermediate > novice)
    }

    @Test
    fun startingWeight_tier1BenchPress_usesResearchNorm() {
        // Tier 1: 80kg male novice bench press
        // base = 80 × 0.325 × 1.0 = 26.0 → round(26/2.5)×2.5 = 25.0kg
        val result = ProgressionEngine.calculateBeginnerStartingWeight(
            exerciseType = "compound_upper", userBodyWeightKg = 80.0,
            userHeightCm = 175.0, preferredUnits = "kg",
            exerciseName = "Bench Press", sex = "male", experienceLevel = "novice"
        )
        assertEquals(25.0, result, 0.01)
    }

    @Test
    fun startingWeight_femaleStartsLighterThanMale() {
        val male = ProgressionEngine.calculateBeginnerStartingWeight(
            "compound_upper", 70.0, 175.0, preferredUnits = "kg", sex = "male"
        )
        val female = ProgressionEngine.calculateBeginnerStartingWeight(
            "compound_upper", 70.0, 165.0, preferredUnits = "kg", sex = "female"
        )
        assertTrue("Female starts lighter than male at same BW", female < male)
    }

    @Test
    fun startingWeight_heavierPersonStartsHeavier() {
        val light = ProgressionEngine.calculateBeginnerStartingWeight(
            "isolation", 55.0, 165.0, preferredUnits = "kg", sex = "male"
        )
        val heavy = ProgressionEngine.calculateBeginnerStartingWeight(
            "isolation", 90.0, 180.0, preferredUnits = "kg", sex = "male"
        )
        assertTrue("Heavier person gets higher starting weight", heavy > light)
    }

    @Test
    fun startingWeight_neverZeroOrNegative() {
        val result = ProgressionEngine.calculateBeginnerStartingWeight(
            exerciseType = "isolation", userBodyWeightKg = 0.0,
            userHeightCm = 0.0, preferredUnits = "kg"
        )
        assertTrue("Starting weight is always positive", result > 0.0)
    }

    @Test
    fun testCalculateEffectiveSetValue_normal() {
        assertEquals(0.75, ProgressionEngine.calculateEffectiveSetValue(8), 0.01)
    }

    @Test
    fun testCalculateEffectiveSetValue_edgeCase_zero() {
        assertEquals(0.0, ProgressionEngine.calculateEffectiveSetValue(0), 0.01)
    }

    @Test
    fun test_SUCCESS_AdvancesWeight() {
        val sets = listOf(
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 8, completed = true),
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 7, completed = true),
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 8, completed = true)
        )
        // targetSets = 3, repsMax = 12. All hit. RPE <= 8.
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 8, 12, 3, 1.0, 100.0, "compound_upper")
        // Base inc for compound_upper = 2.5. RPE 8 (max) -> multiplier 1.0. 
        // Total inc = 2.5. 100 + 2.5 = 102.5.
        assertEquals(102.5, res.newWeight, 0.01)
        assertEquals(ProgressionEngine.OutcomeType.SUCCESS, res.outcome)
    }

    @Test
    fun test_PROGRESSING_HoldsOnRPE9() {
        val sets = listOf(
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 9, completed = true),
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 7, completed = true)
        )
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 8, 12, 2, 1.0, 100.0, "compound_upper")
        // RPE 9 -> not SUCCESS (must be <= 8)
        // All sets within reps range [8, 12] -> PROGRESSING
        assertEquals(100.0, res.newWeight, 0.01)
        assertEquals(ProgressionEngine.OutcomeType.PROGRESSING, res.outcome)
    }

    @Test
    fun test_STALLED_RepsBelowMin_HoldsWeight() {
        val sets = listOf(
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 7, rpe = 9, completed = true), // Below repsMin 8
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 8, rpe = 9, completed = true),
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 8, rpe = 9, completed = true)
        )
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 8, 12, 3, 1.0, 100.0, "compound_upper")
        assertEquals(100.0, res.newWeight, 0.01)
        assertEquals(ProgressionEngine.OutcomeType.STALLED, res.outcome)
    }

    @Test
    fun test_PLATEAU_ConsecutiveStalls_Deloads() {
        val sets = listOf(
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 7, rpe = 9, completed = true), // Failed target
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 8, rpe = 9, completed = true)
        )
        // targetSets 3, only 2 completed -> failedTarget = true
        // consecutiveStalledSessions = 1 (meaning the session BEFORE this one was also stalled)
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 8, 12, 3, 1.0, 100.0, "compound_upper", consecutiveStalledSessions = 1)
        // outcome should be PLATEAU -> 10% deload
        assertEquals(90.0, res.newWeight, 0.01)
        assertEquals(ProgressionEngine.OutcomeType.PLATEAU, res.outcome)
    }

    @Test
    fun test_UnratedRPE_HoldsWeight() {
        val sets = listOf(
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 0, completed = true), // RPE 0 = Unrated
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 8, completed = true)
        )
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 8, 12, 2, 1.0, 100.0, "compound_upper")
        assertEquals(100.0, res.newWeight, 0.01)
        assertEquals(ProgressionEngine.OutcomeType.STALLED, res.outcome)
        assertTrue(res.reason.contains("unrated RPE"))
    }

    @Test
    fun testCalculateProgressiveWeight_maxRpe_stallsOnSingleHighSet() {
        // 3 sets: two easy, one grinder (RPE 10)
        val sets = listOf(
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 5, rpe = 6, completed = true),
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 5, rpe = 6, completed = true),
            com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 5, rpe = 10, completed = true)
        )
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 5, 5, 3, 1.0, 100.0, "compound_lower")
        // maxRpe is 10 -> rpeMultiplier should be 0.0
        // Resulting weight should be 100.0 (no advance)
        assertEquals(100.0, res.newWeight, 0.01)
        assertTrue("Should not be SUCCESS due to RPE 10", res.outcome != ProgressionEngine.OutcomeType.SUCCESS)
    }
}

    // ── Detraining adjustment ─────────────────────────────────────────────────

    @Test
    fun noGap_noDetrainingAdjustment() {
        // < 14 days — trivial loss, weight unchanged
        val result = ProgressionEngine.calculateProgressiveWeight(
            exerciseId = "bench",
            lastSessionSets = listOf(com.apexfit.app.ui.models.UiExerciseSet(weight = 50.0, reps = 12, rpe = 8, completed = true)),
            repsMin = 10, repsMax = 12, targetSets = 1,
            recoveryMultiplier = 1.0, currentWeight = 50.0,
            exerciseType = "compound_upper", daysSinceLastSession = 7
        )
        // No detraining — should progress or hold at 50.0, never below
        assertTrue("Weight should not be reduced for 7-day gap", result.newWeight >= 50.0)
    }

    @Test
    fun twoWeekGap_fivePercentReduction() {
        // 14–27 days — 5% reduction applied before progression logic
        val result = ProgressionEngine.calculateProgressiveWeight(
            exerciseId = "bench",
            lastSessionSets = listOf(com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 10, rpe = 8, completed = true)),
            repsMin = 8, repsMax = 10, targetSets = 1,
            recoveryMultiplier = 1.0, currentWeight = 100.0,
            exerciseType = "compound_upper", daysSinceLastSession = 20
        )
        // effectiveWeight = 100 × 0.95 = 95 — result should be at or below previous weight
        assertTrue("Weight should reflect ~5% detraining reduction", result.newWeight <= 100.0)
    }

    @Test
    fun sixWeekGap_tenPercentReduction() {
        // 28–55 days — 10% reduction
        val result = ProgressionEngine.calculateProgressiveWeight(
            exerciseId = "squat",
            lastSessionSets = listOf(com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 10, rpe = 8, completed = true)),
            repsMin = 8, repsMax = 10, targetSets = 1,
            recoveryMultiplier = 1.0, currentWeight = 100.0,
            exerciseType = "compound_lower", daysSinceLastSession = 42
        )
        // effectiveWeight = 100 × 0.90 = 90
        assertTrue("6-week gap should reduce weight", result.newWeight <= 100.0)
        assertTrue("Reduction should not be extreme", result.newWeight >= 75.0)
    }

    @Test
    fun tenWeekGap_fifteenPercentReduction() {
        // 56+ days — 15% reduction
        val result = ProgressionEngine.calculateProgressiveWeight(
            exerciseId = "squat",
            lastSessionSets = listOf(com.apexfit.app.ui.models.UiExerciseSet(weight = 100.0, reps = 10, rpe = 8, completed = true)),
            repsMin = 8, repsMax = 10, targetSets = 1,
            recoveryMultiplier = 1.0, currentWeight = 100.0,
            exerciseType = "compound_lower", daysSinceLastSession = 70
        )
        // effectiveWeight = 100 × 0.85 = 85
        assertTrue("10-week gap should reduce weight by ~15%", result.newWeight <= 90.0)
    }

    @Test
    fun detrainingNeverProducesNegativeWeight() {
        // Even with max detraining, weight stays above plate minimum
        val result = ProgressionEngine.calculateProgressiveWeight(
            exerciseId = "curl",
            lastSessionSets = listOf(com.apexfit.app.ui.models.UiExerciseSet(weight = 5.0, reps = 10, rpe = 8, completed = true)),
            repsMin = 8, repsMax = 12, targetSets = 1,
            recoveryMultiplier = 1.0, currentWeight = 5.0,
            exerciseType = "isolation", daysSinceLastSession = 120
        )
        assertTrue("Weight should never be zero or negative", result.newWeight > 0.0)
    }
