package com.apexfit.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalculatorEngineTest {

    @Test
    fun customPlateSetFindsExactCombinationThatGreedyMisses() {
        val result = PlateCalculatorEngine.calculatePlatesPerSide(
            targetWeight = 32.0,
            barWeight = 20.0,
            availablePlates = listOf(4.0, 3.0)
        )

        assertTrue(result.isExact)
        assertEquals(32.0, result.achievedWeight, 0.0001)
        assertEquals(listOf(3.0, 3.0), result.platesPerSide)
    }

    @Test
    fun closestReachableLoadNeverOvershootsTarget() {
        val result = PlateCalculatorEngine.calculatePlatesPerSide(
            targetWeight = 30.0,
            barWeight = 20.0,
            availablePlates = listOf(4.0, 3.0)
        )

        assertFalse(result.isExact)
        assertTrue(result.achievedWeight <= 30.0)
        assertEquals(2.0, result.remainingDifference, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeBarWeightIsRejected() {
        PlateCalculatorEngine.calculatePlatesPerSide(
            targetWeight = 50.0,
            barWeight = -20.0
        )
    }
}
