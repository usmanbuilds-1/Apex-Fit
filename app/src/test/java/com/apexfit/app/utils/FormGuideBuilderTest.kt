package com.apexfit.app.utils

import com.apexfit.app.data.Exercise
import com.apexfit.app.data.LastSetWithDate
import com.apexfit.app.data.PersonalRecord
import com.apexfit.app.data.PlanExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    @Test
    fun planAndTrainingHistoryAreCarriedAsRealExerciseDetailContext() {
        val exercise = Exercise(
            id = "dumbbell-lateral-raise",
            name = "Dumbbell Lateral Raise",
            category = "Dumbbell",
            primaryMuscle = "Shoulders",
            equipmentRequired = "Dumbbell"
        )
        val prescription = PlanExercise(
            planSessionId = 1,
            exerciseId = exercise.id,
            name = exercise.name,
            muscleGroup = "Shoulders",
            sets = 3,
            repsMin = 12,
            repsMax = 15,
            weight = 14.0,
            restSeconds = 90,
            notes = "Keep the reps controlled."
        )
        val set = LastSetWithDate(
            id = 7,
            sessionId = "session-1",
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            muscleGroup = "Shoulders",
            weight = 14.0,
            reps = 14,
            rpe = 8,
            isWarmup = false,
            restTaken = 90,
            completed = true,
            date = "2026-11-18"
        )
        val record = PersonalRecord("${exercise.id}_max_weight", exercise.id, "max_weight", 16.0, "2026-11-19")

        val result = FormGuideBuilder.buildFormGuide(
            exercise = exercise,
            metadata = null,
            prescription = prescription,
            recentHistory = listOf(set),
            personalRecords = listOf(record)
        )

        assertSame(prescription, result.prescription)
        assertEquals(listOf(set), result.recentHistory)
        assertEquals(listOf(record), result.personalRecords)
    }

    @Test
    fun alternativesUseActiveExercisesWithTheSamePrimaryMuscleOnly() {
        val exercise = Exercise(
            id = "dumbbell-lateral-raise",
            name = "Dumbbell Lateral Raise",
            category = "Dumbbell",
            primaryMuscle = "Shoulders",
            equipmentRequired = "Dumbbell"
        )
        val catalog = listOf(
            exercise,
            exercise.copy(id = "cable-lateral-raise", name = "Cable Lateral Raise", equipmentRequired = "Cable"),
            exercise.copy(id = "machine-lateral-raise", name = "Machine Lateral Raise", equipmentRequired = "Machine"),
            exercise.copy(id = "shoulder-press", name = "Shoulder Press", primaryMuscle = "Chest",
                secondaryMuscles = listOf("Shoulders")),
            exercise.copy(id = "deleted-raise", name = "Deleted Raise", isDeleted = 1)
        )

        val result = FormGuideBuilder.buildFormGuide(
            exercise = exercise,
            metadata = null,
            exerciseCatalog = catalog
        )

        assertEquals(listOf("cable-lateral-raise", "machine-lateral-raise"),
            result.alternativeExercises.map { it.id })
    }
}
