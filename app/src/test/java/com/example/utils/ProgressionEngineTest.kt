package com.example.utils

import org.junit.Assert.*
import org.junit.Test

class ProgressionEngineTest {

    @Test
    fun testCalculateProgressiveWeight_normal() {
        val lastWeight = 100.0
        val lastRPE = 8
        val daysSinceLastSession = 3
        val bodyWeightLbs = 180.0
        val exerciseType = "compound_lower"
        val res = ProgressionEngine.calculateProgressiveWeight(lastWeight, lastRPE, daysSinceLastSession, bodyWeightLbs, exerciseType)
        // Base inc = 10.0
        // RPE 8 -> 1.0
        // Days 3 -> 1.0
        // BW 180 -> 1.0
        // Total inc = 10.0 * 1.0 * 1.0 * 1.0 = 10.0
        // Output = 110.0
        assertEquals(110.0, res, 0.01)
    }

    @Test
    fun testCalculateProgressiveWeight_edgeCase_zero() {
        val res = ProgressionEngine.calculateProgressiveWeight(0.0, 0, 0, 0.0, "isolation")
        // Base inc = 2.5
        // RPE 0 -> 1.0 (else branch)
        // Days 0 -> 0.5
        // BW 0 -> 0.75
        // Total inc = 2.5 * 1.0 * 0.5 * 0.75 = 0.9375
        // Nearest 2.5 of 0.9375 -> 0.0! Wait, let's trace:
        // (0.9375 / 2.5) = 0.375 -> round = 0
        // 0 * 2.5 = 0.0
        assertEquals(0.0, res, 0.01)
    }

    @Test
    fun testCalculateProgressiveWeight_boundary_RPE7() {
        val res = ProgressionEngine.calculateProgressiveWeight(100.0, 7, 3, 180.0, "compound_upper")
        // Base inc = 5.0
        // RPE 7 -> 0.5
        // Days 3 -> 1.0
        // BW 180 -> 1.0
        // Total inc = 2.5. 100 + 2.5 = 102.5
        assertEquals(102.5, res, 0.01)
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
    fun testCalculateEffectiveSetValue_boundary_RPE7() {
        assertEquals(0.5, ProgressionEngine.calculateEffectiveSetValue(7), 0.01)
    }
}
