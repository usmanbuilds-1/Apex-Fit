package com.apexfit.app.utils

import com.apexfit.app.data.PlanExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartingLoadCalibrationTest {
    private fun planExercise(
        weightKg: Double = 0.0,
        repsMin: Int = 6,
        repsMax: Int = 10
    ) = PlanExercise(
        planSessionId = 1,
        exerciseId = "barbell-bench-press",
        name = "Barbell Bench Press",
        muscleGroup = "Chest",
        sets = 3,
        repsMin = repsMin,
        repsMax = repsMax,
        weight = weightKg,
        restSeconds = 120,
        notes = ""
    )

    @Test
    fun unweightedFirstExposureStartsAtEditableZeroWithThePlansRepAndEffortTargets() {
        val calibration = StartingLoadCalibrationBuilder.forFirstExposure(planExercise(), "Barbell")

        assertEquals(0.0, calibration.initialLoadKg, 0.0)
        assertTrue(calibration.guidance.contains("comfortable starting load"))
        assertTrue(calibration.guidance.contains("6–10 reps"))
        assertTrue(calibration.guidance.contains("2–3 RIR"))
    }

    @Test
    fun aUserAuthoredPlanLoadIsPrefilledButStillPresentedAsCalibratable() {
        val calibration = StartingLoadCalibrationBuilder.forFirstExposure(
            planExercise(weightKg = 25.0), "Barbell"
        )

        assertEquals(25.0, calibration.initialLoadKg, 0.0)
        assertTrue(calibration.guidance.contains("adjust the plan load"))
    }

    @Test
    fun bodyweightFirstExposureKeepsExternalLoadSeparate() {
        val calibration = StartingLoadCalibrationBuilder.forFirstExposure(
            planExercise(weightKg = 0.0, repsMin = 8, repsMax = 12), "Bodyweight"
        )

        assertEquals(0.0, calibration.initialLoadKg, 0.0)
        assertTrue(calibration.guidance.contains("Record any added load separately"))
        assertTrue(calibration.guidance.contains("8–12 reps"))
    }

    @Test
    fun firstExposureLoadUsesCanonicalKilogramsForBothDisplayUnits() {
        val calibration = StartingLoadCalibrationBuilder.forFirstExposure(
            planExercise(weightKg = 25.0), "Barbell"
        )

        assertEquals(25.0, calibration.initialLoadKg.toDisplayWeight("kg"), 0.0)
        assertEquals(55.1, calibration.initialLoadKg.toDisplayWeight("lb"), 0.001)
    }
}
