package com.apexfit.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormGuideBuilderTest {

    @Test
    fun numberedSourceNotes_areSplitIntoReadableSteps() {
        val notes = """
            1. Set your feet and brace.
            2. Raise the weight under control.
            3. Lower slowly.
        """.trimIndent()

        assertEquals(
            listOf(
                "Set your feet and brace.",
                "Raise the weight under control.",
                "Lower slowly."
            ),
            FormGuideBuilder.parseNumberedInstructions(notes)
        )
    }

    @Test
    fun sourceInstructions_arePreferredOverLegacyNotes() {
        val exercise = com.apexfit.app.data.Exercise(
            id = "dumbbell-lateral-raise",
            name = "Dumbbell Lateral Raise",
            category = "Dumbbell",
            primaryMuscle = "Shoulders",
            equipmentRequired = "Dumbbell"
        )
        val metadata = com.apexfit.app.data.ExerciseMetadata(
            exerciseId = exercise.id,
            notes = "1. Legacy step.\n2. Legacy finish."
        )

        val result = FormGuideBuilder.buildFormGuide(
            exercise = exercise,
            metadata = metadata,
            sourceInstructions = listOf("Stand tall.", "Raise the dumbbells under control.")
        )

        assertEquals(
            listOf("Stand tall.", "Raise the dumbbells under control."),
            result.instructions
        )
    }

    @Test
    fun ordinaryCoachingNote_isNotPretendedToBeStepByStepInstructions() {
        assertTrue(FormGuideBuilder.parseNumberedInstructions("Keep full range of motion.").isEmpty())
    }
}
