package com.apexfit.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanExerciseDraftValidatorTest {
    @Test
    fun validPoundsInputIsStoredAsKilogramsAndKeepsRepRange() {
        val result = validatePlanExerciseDraft(
            name = "  Dumbbell Press  ",
            setsText = "3",
            minRepsText = "8",
            maxRepsText = "12",
            weightText = "33.1",
            weightUnit = "lb",
            restText = "90"
        ) as PlanExerciseDraftValidation.Valid

        assertEquals("Dumbbell Press", result.name)
        assertEquals(3, result.sets)
        assertEquals(8, result.minReps)
        assertEquals(12, result.maxReps)
        assertEquals(15.0, result.weightKg, 0.1)
        assertEquals(90, result.restSeconds)
    }

    @Test
    fun blankWeightRemainsAnUncalibratedZeroLoad() {
        val result = validatePlanExerciseDraft(
            name = "Bodyweight Squat",
            setsText = "3",
            minRepsText = "10",
            maxRepsText = "15",
            weightText = "",
            weightUnit = "kg",
            restText = "0"
        ) as PlanExerciseDraftValidation.Valid

        assertEquals(0.0, result.weightKg, 0.0)
        assertEquals(0, result.restSeconds)
    }

    @Test
    fun malformedOrBlankIntegersFailInsteadOfUsingSuggestedDefaults() {
        val cases = listOf(
            Triple("", "8", "12") to PlanExerciseDraftField.SETS,
            Triple("three", "8", "12") to PlanExerciseDraftField.SETS,
            Triple("3", "", "12") to PlanExerciseDraftField.MIN_REPS,
            Triple("3", "8", "twelve") to PlanExerciseDraftField.MAX_REPS
        )

        cases.forEach { (values, expectedField) ->
            val result = validatePlanExerciseDraft(
                name = "Press",
                setsText = values.first,
                minRepsText = values.second,
                maxRepsText = values.third,
                weightText = "",
                weightUnit = "kg",
                restText = "90"
            )

            assertEquals(expectedField, (result as PlanExerciseDraftValidation.Invalid).field)
        }
    }

    @Test
    fun rejectsBadNameRepOrderRestAndWeight() {
        fun validate(
            name: String = "Press",
            min: String = "8",
            max: String = "12",
            weight: String = "",
            rest: String = "90"
        ) = validatePlanExerciseDraft(name, "3", min, max, weight, "kg", rest)

        assertInvalid(validate(name = " "), PlanExerciseDraftField.NAME)
        assertInvalid(validate(min = "13", max = "12"), PlanExerciseDraftField.MIN_REPS)
        assertInvalid(validate(rest = "-1"), PlanExerciseDraftField.REST)
        assertInvalid(validate(rest = "rest"), PlanExerciseDraftField.REST)
        assertInvalid(validate(weight = "301"), PlanExerciseDraftField.WEIGHT)
        assertInvalid(validate(weight = "NaN"), PlanExerciseDraftField.WEIGHT)
    }

    private fun assertInvalid(
        result: PlanExerciseDraftValidation,
        expectedField: PlanExerciseDraftField
    ) {
        assertTrue(result is PlanExerciseDraftValidation.Invalid)
        assertEquals(expectedField, (result as PlanExerciseDraftValidation.Invalid).field)
    }
}
