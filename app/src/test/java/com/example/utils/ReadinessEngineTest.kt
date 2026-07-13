package com.example.utils

import org.junit.Assert.*
import org.junit.Test

class ReadinessEngineTest {

    @Test
    fun testReadinessFinalCalculate_coldStart() {
        val res = ReadinessFinal.calculate(
            totalCompletedSessions = 2, // Cold start < 3
            todaysMuscleGroups = listOf("chest"),
            muscleFatigueHistory = emptyMap(),
            muscleSessionHistory = emptyMap(),
            systemicHistory = emptyList(),
            systemicCapacity = 1500.0,
            nutritionScore = 80,
            acuteLoad = 0.0,
            chronicLoad = 0.0
        )
        // Check overriding logic for cold start
        assertEquals("Building Baseline", res.label)
        assertEquals("limited - building baseline", res.dataConfidence)
    }

    @Test
    fun testReadinessFinalCalculate_normalCase() {
        val res = ReadinessFinal.calculate(
            totalCompletedSessions = 10,
            todaysMuscleGroups = listOf("chest"),
            muscleFatigueHistory = mapOf("chest" to listOf(MuscleFatigueSnapshot("chest", 1.0, 100.0))),
            muscleSessionHistory = mapOf("chest" to listOf(100.0, 120.0, 110.0)),
            systemicHistory = listOf(SystemicCNSCalculator.SystemicSnapshot(1.0, 200.0)),
            systemicCapacity = 1500.0,
            nutritionScore = 90,
            acuteLoad = 1.0,
            chronicLoad = 1.0
        )
        assertNotEquals("Building Baseline", res.label)
        assertTrue(res.overallPercent in 0..100)
        assertEquals("full", res.dataConfidence)
    }

    @Test
    fun testReadinessFinalCalculate_boundary_noFatigue() {
        val res = ReadinessFinal.calculate(
            totalCompletedSessions = 10,
            todaysMuscleGroups = emptyList(),
            muscleFatigueHistory = emptyMap(),
            muscleSessionHistory = emptyMap(),
            systemicHistory = emptyList(),
            systemicCapacity = 1500.0,
            nutritionScore = 100,
            acuteLoad = 0.0,
            chronicLoad = 0.0
        )
        assertEquals(89, res.overallPercent)
    }
}
