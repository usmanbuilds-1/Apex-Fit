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
    fun ordinaryCoachingNote_isNotPretendedToBeStepByStepInstructions() {
        assertTrue(FormGuideBuilder.parseNumberedInstructions("Keep full range of motion.").isEmpty())
    }
}
