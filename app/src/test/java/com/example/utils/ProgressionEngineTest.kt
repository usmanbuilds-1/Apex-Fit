package com.example.utils

import org.junit.Assert.*
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionEngineTest {

    @Test
    fun testCalculateProgressiveWeight_normal() {
        // Need to create dummy sets
        val sets = listOf(com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 5, rpe = 8, completed = true))
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 5, 5, 1, 1.0, 100.0, "compound_lower")
        // Base inc = 5.0 (for compound_lower)
        // RPE 8 -> 1.0 multiplier
        // Total inc = 5.0 * 1.0 * 1.0 = 5.0
        // Output = 105.0
        assertEquals(105.0, res.newWeight, 0.01)
    }

    @Test
    fun testCalculateProgressiveWeight_edgeCase_zero() {
        val sets = listOf(com.example.ui.models.UiExerciseSet(weight = 0.0, reps = 0, rpe = 0))
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 5, 5, 1, 1.0, 0.0, "isolation")
        assertEquals(0.0, res.newWeight, 0.01)
    }

    @Test
    fun testCalculateProgressiveWeight_boundary_RPE7() {
        val sets = listOf(com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 5, rpe = 7, completed = true))
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 5, 5, 1, 1.0, 100.0, "compound_upper")
        // Base inc = 2.5 (for compound_upper)
        // RPE 7 -> 1.0 multiplier
        // Total inc = 2.5 * 1.0 * 1.0 = 2.5. 100 + 2.5 = 102.5.
        assertEquals(102.5, res.newWeight, 0.01)
    }

    @Test
    fun testCalculateBeginnerStartingWeight_normal() {
        val res = ProgressionEngine.calculateBeginnerStartingWeight("compound_upper", 150.0, 175.0)
        // base = 0.35. 150 * 0.35 = 52.5
        // height 175 -> no multiplier
        // 52.5 nearest 2.5 = 52.5
        assertEquals(52.5, res, 0.01)
    }

    @Test
    fun testCalculateBeginnerStartingWeight_edgeCase_zero() {
        val res = ProgressionEngine.calculateBeginnerStartingWeight("", 0.0, 0.0)
        assertEquals(0.0, res, 0.01)
    }

    @Test
    fun testCalculateBeginnerStartingWeight_boundary_height() {
        val res = ProgressionEngine.calculateBeginnerStartingWeight("compound_upper", 150.0, 185.1)
        // base = 0.35. 150 * 0.35 = 52.5
        // height > 185 -> * 0.9 = 47.25
        // 47.25 nearest 2.5: 47.25 / 2.5 = 18.9 -> 19 -> 19 * 2.5 = 47.5
        assertEquals(47.5, res, 0.01)
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
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 8, completed = true),
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 7, completed = true),
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 8, completed = true)
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
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 9, completed = true),
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 7, completed = true)
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
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 7, rpe = 9, completed = true), // Below repsMin 8
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 8, rpe = 9, completed = true),
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 8, rpe = 9, completed = true)
        )
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 8, 12, 3, 1.0, 100.0, "compound_upper")
        assertEquals(100.0, res.newWeight, 0.01)
        assertEquals(ProgressionEngine.OutcomeType.STALLED, res.outcome)
    }

    @Test
    fun test_PLATEAU_ConsecutiveStalls_Deloads() {
        val sets = listOf(
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 7, rpe = 9, completed = true), // Failed target
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 8, rpe = 9, completed = true)
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
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 0, completed = true), // RPE 0 = Unrated
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 12, rpe = 8, completed = true)
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
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 5, rpe = 6, completed = true),
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 5, rpe = 6, completed = true),
            com.example.ui.models.UiExerciseSet(weight = 100.0, reps = 5, rpe = 10, completed = true)
        )
        val res = ProgressionEngine.calculateProgressiveWeight("ex1", sets, 5, 5, 3, 1.0, 100.0, "compound_lower")
        // maxRpe is 10 -> rpeMultiplier should be 0.0
        // Resulting weight should be 100.0 (no advance)
        assertEquals(100.0, res.newWeight, 0.01)
        assertTrue("Should not be SUCCESS due to RPE 10", res.outcome != ProgressionEngine.OutcomeType.SUCCESS)
    }
}
