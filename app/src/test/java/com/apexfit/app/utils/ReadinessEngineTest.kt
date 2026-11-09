package com.apexfit.app.utils

import org.junit.Assert.*
import org.junit.Test

class ReadinessEngineTest {
    @Test
    fun workloadRatioDoesNotDetermineReadinessScore() {
        fun score(acute: Double) = ReadinessFinal.calculate(
            totalCompletedSessions = 5,
            todaysMuscleGroups = emptyList(),
            muscleFatigueHistory = emptyMap(),
            muscleSessionHistory = emptyMap(),
            systemicHistory = emptyList(),
            systemicCapacity = 1500.0,
            nutritionScore = 80,
            acuteLoad = acute,
            chronicLoad = 100.0
        ).overallPercent
        assertEquals(score(100.0), score(200.0))
    }


    @Test
    fun readinessMatchesTrainingHistoryAcrossMuscleAliases() {
        val workout = com.apexfit.app.data.RichTrainingSession(
            date = getCurrentDate(), sessionType = "Pull", completed = true,
            exercises = listOf(com.apexfit.app.data.ExerciseLog(
                id = "row", name = "Row", muscleGroup = "Back",
                sets = listOf(com.apexfit.app.data.ExerciseSet(50.0, 10, 8, false, true))
            ))
        )
        fun score(muscle: String) = ReadinessFinal.buildReadinessInputs(
            completedSessions = listOf(workout),
            todayExercises = listOf(com.apexfit.app.data.PlanExercise(
                planSessionId = 1, name = "Row", muscleGroup = muscle,
                sets = 3, repsMin = 8, repsMax = 12, weight = 50.0,
                restSeconds = 120, notes = ""
            )),
            nutritionLog = emptyList(),
            targets = com.apexfit.app.data.NutritionTargets(2000, 120, 200, 60)
        ).muscleDetails.single().readinessPercent

        assertEquals(score("Back"), score("Lats"))
        assertTrue("Training history should reduce estimated readiness", score("Back") < 100)
    }

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
            systemicHistory = listOf(SystemicFatigueCalculator.SystemicSnapshot(1.0, 200.0)),
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
