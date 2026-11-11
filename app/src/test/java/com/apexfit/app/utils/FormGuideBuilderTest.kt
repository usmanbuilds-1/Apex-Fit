package com.apexfit.app.utils

import com.apexfit.app.data.Exercise
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormGuideBuilderTest {
    @Test
    fun starterExercisesHaveMovementSpecificGuidance() {
        val ids = listOf(
            "flat-dumbbell-press", "chest-supported-dumbbell-row", "dumbbell-lateral-raise",
            "hammer-bicep-curl", "leg-press-high-wide", "romanian-deadlift",
            "seated-leg-curl", "standing-calf-raise", "standing-overhead-press",
            "lat-pulldown-neutral-grip", "dual-rope-tricep-pushdown"
        )
        ids.forEach { id ->
            val exercise = Exercise(id = id, name = id, category = "Test",
                primaryMuscle = "Muscle", equipmentRequired = "Equipment")
            val guide = FormGuideBuilder.buildFormGuide(exercise, null)
            assertFalse("Missing guide for $id", guide.setup.contains("not available"))
            assertTrue("Missing execution for $id", guide.execution.isNotBlank())
            assertTrue("Missing mistake for $id", guide.commonMistake.isNotBlank())
        }
    }

    @Test
    fun unknownExerciseIsNotPresentedAsMovementSpecific() {
        val exercise = Exercise(id = "custom-exercise", name = "Custom exercise", category = "Other",
            primaryMuscle = "Back", equipmentRequired = "Other")
        val guide = FormGuideBuilder.buildFormGuide(exercise, null)
        assertTrue(guide.setup.contains("not available"))
        assertFalse(guide.muscleNote.contains("highly isolated"))
    }
}
