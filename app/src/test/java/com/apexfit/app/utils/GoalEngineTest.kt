package com.apexfit.app.utils

import org.junit.Assert.*
import org.junit.Test

class GoalEngineTest {

    // ── LBM (Boer formula) ────────────────────────────────────
    //
    // Male:   LBM = (0.407 × weight_kg) + (0.267 × height_cm) - 19.2
    // Female: LBM = (0.252 × weight_kg) + (0.473 × height_cm) - 48.3
    // Source: Boer P. (1984). Am J Physiol.

    @Test
    fun estimateLBM_male_matchesBoerFormula() {
        // (0.407 × 80) + (0.267 × 175) - 19.2 = 32.56 + 46.725 - 19.2 = 60.085
        val lbm = GoalEngine.estimateLBM(weightKg = 80.0, heightCm = 175.0, sex = "male")
        assertEquals(60.085, lbm, 0.01)
    }

    @Test
    fun estimateLBM_female_matchesBoerFormula() {
        // (0.252 × 60) + (0.473 × 165) - 48.3 = 15.12 + 78.045 - 48.3 = 44.865
        val lbm = GoalEngine.estimateLBM(weightKg = 60.0, heightCm = 165.0, sex = "female")
        assertEquals(44.865, lbm, 0.01)
    }

    @Test
    fun estimateLBM_sexIsCaseInsensitive() {
        val male1 = GoalEngine.estimateLBM(80.0, 175.0, "male")
        val male2 = GoalEngine.estimateLBM(80.0, 175.0, "Male")
        val male3 = GoalEngine.estimateLBM(80.0, 175.0, "MALE")
        assertEquals(male1, male2, 0.001)
        assertEquals(male1, male3, 0.001)
    }

    @Test
    fun estimateLBM_maleLBM_lessThanTotalWeight() {
        // LBM should always be less than total body weight for reasonable inputs
        val lbm = GoalEngine.estimateLBM(weightKg = 80.0, heightCm = 175.0, sex = "male")
        assertTrue("LBM $lbm should be less than body weight 80kg", lbm < 80.0)
    }

    @Test
    fun estimateLBM_femaleLBM_lessThanTotalWeight() {
        val lbm = GoalEngine.estimateLBM(weightKg = 65.0, heightCm = 165.0, sex = "female")
        assertTrue("LBM $lbm should be less than body weight 65kg", lbm < 65.0)
    }

    @Test
    fun estimateLBM_tallerPerson_higherLBM() {
        val lbmShort = GoalEngine.estimateLBM(80.0, 165.0, "male")
        val lbmTall  = GoalEngine.estimateLBM(80.0, 185.0, "male")
        assertTrue("Taller person LBM $lbmTall should exceed shorter person LBM $lbmShort",
            lbmTall > lbmShort)
    }

    // ── Goal timeline ─────────────────────────────────────────

    @Test
    fun gainMuscle_weeklyRate_clampedToRealisticRange() {
        // 80kg × 0.004 = 0.32 → within [0.15, 0.35]
        val timeline = GoalEngine.calcGoalTimeline(80.0, 90.0, "gain muscle", 175.0, "male")
        assertEquals(0.32, timeline.weeklyChangeKg, 0.001)
    }

    @Test
    fun gainMuscle_veryLightPerson_rateClampedAtMinimum() {
        // 30kg × 0.004 = 0.12 → clamped to 0.15
        val timeline = GoalEngine.calcGoalTimeline(30.0, 35.0, "gain muscle", 160.0, "female")
        assertEquals(0.15, timeline.weeklyChangeKg, 0.001)
    }

    @Test
    fun gainMuscle_veryHeavyPerson_rateClampedAtMaximum() {
        // 200kg × 0.004 = 0.8 → clamped to 0.35
        val timeline = GoalEngine.calcGoalTimeline(200.0, 210.0, "gain muscle", 190.0, "male")
        assertEquals(0.35, timeline.weeklyChangeKg, 0.001)
    }

    @Test
    fun loseFat_weeklyRate_clampedToSafeRange() {
        // 80kg × 0.008 = 0.64 → within [0.30, 0.70]
        val timeline = GoalEngine.calcGoalTimeline(80.0, 70.0, "lose fat", 175.0, "male")
        assertEquals(0.64, timeline.weeklyChangeKg, 0.001)
    }

    @Test
    fun recomposition_weeklyRate_isZero() {
        val timeline = GoalEngine.calcGoalTimeline(75.0, 75.0, "recomposition", 170.0, "male")
        assertEquals(0.0, timeline.weeklyChangeKg, 0.001)
        assertEquals(0, timeline.weeksToGoal)
    }

    @Test
    fun reachableGoal_isMarkedRealistic() {
        // 5kg gain at 0.32 kg/week = ~16 weeks → well under 104 weeks
        val timeline = GoalEngine.calcGoalTimeline(75.0, 80.0, "gain muscle", 175.0, "male")
        assertTrue("15-week goal should be marked realistic", timeline.isRealistic)
    }

    @Test
    fun unreachableGoal_isMarkedUnrealistic() {
        // 80kg gain at 0.35 kg/week = ~229 weeks → over 104 week limit
        val timeline = GoalEngine.calcGoalTimeline(60.0, 140.0, "gain muscle", 175.0, "male")
        assertFalse("228-week goal should be marked unrealistic", timeline.isRealistic)
    }

    @Test
    fun unrealisticGoal_adjustedToOneYearMilestone() {
        // When goal is unrealistic, adjustedGoalWeightKg = current + direction × rate × 52
        val current = 60.0
        val rate = (current * 0.004).coerceIn(0.15, 0.35) // 0.24 kg/week
        val expectedAdjusted = current + rate * 52.0
        val timeline = GoalEngine.calcGoalTimeline(current, 140.0, "gain muscle", 175.0, "male")
        assertEquals(expectedAdjusted, timeline.adjustedGoalWeightKg, 0.01)
    }

    @Test
    fun weeksToGoal_neverExceeds104() {
        val timeline = GoalEngine.calcGoalTimeline(50.0, 200.0, "gain muscle", 175.0, "male")
        assertTrue("weeksToGoal ${timeline.weeksToGoal} should be capped at 104",
            timeline.weeksToGoal <= 104)
    }

    @Test
    fun summaryLine_neverBlank() {
        listOf("gain muscle", "lose fat", "recomposition", "maintain").forEach { goal ->
            val timeline = GoalEngine.calcGoalTimeline(75.0, 80.0, goal, 175.0, "male")
            assertTrue("Summary for '$goal' should not be blank", timeline.summaryLine.isNotBlank())
        }
    }
}
