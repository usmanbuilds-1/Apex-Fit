package com.apexfit.app.domain.importer

import org.junit.Assert.*
import org.junit.Test

class ImportedGroupReviewTest {
    private fun ex(key: String, tag: String? = null) = EditableImportedExercise(
        key = key, sourceName = key, selectedExerciseId = key, selectedExerciseName = key,
        muscleGroup = "Back", matchConfidence = MatchConfidence.EXACT, matchConfirmed = true,
        candidates = emptyList(), setsText = "3", repsMinText = "8", repsMaxText = "12",
        weightText = "", weightUnit = ImportWeightUnit.KG, restSecondsText = "90",
        repTarget = ImportRepTarget.FIXED, notes = "", sourcePrescription = null, sourceGroupTag = tag
    )
    private val day = EditableImportedDay(label = "Upper", dayOfWeek = "Monday", focus = null,
        exercises = listOf(ex("1"), ex("2", "A"), ex("3", "A"), ex("4", "B"), ex("5", "B")))
    private val choice = ImportedGroupChoice(listOf("2", "3"), 15, 120)

    @Test fun tagsAreSuggestionsAndOnlyExplicitChoiceActivatesGroup() {
        assertEquals(listOf("A", "B"), day.groupCandidates().map { it.tag })
        assertNull(day.validGroupChoice("A"))
        val confirmed = day.copy(groupChoices = mapOf("A" to choice))
        assertEquals(choice, confirmed.validGroupChoice("A"))
        assertNull(confirmed.validGroupChoice("B"))
        assertTrue(confirmed.groupValidationErrors().isEmpty())
    }

    @Test fun editedOrderOrMissingMemberInvalidatesConfirmation() {
        val confirmed = day.copy(groupChoices = mapOf("A" to choice))
        val reversed = confirmed.copy(exercises = listOf(day.exercises[0], day.exercises[2],
            day.exercises[1], day.exercises[3], day.exercises[4]))
        assertNull(reversed.validGroupChoice("A"))
        assertTrue(reversed.groupValidationErrors().isNotEmpty())
        val removed = confirmed.copy(exercises = confirmed.exercises.filterNot { it.key == "2" })
        assertFalse(removed.groupCandidates().first { it.tag == "A" }.consecutive)
        assertTrue(removed.groupValidationErrors().isNotEmpty())
        assertTrue(removed.copy(groupChoices = emptyMap()).groupValidationErrors().isEmpty())
    }

    @Test fun splitTagAndInvalidRestCannotActivate() {
        val split = day.copy(exercises = listOf(day.exercises[1], day.exercises[0], day.exercises[2],
            day.exercises[3], day.exercises[4]), groupChoices = mapOf("A" to choice))
        assertNull(split.validGroupChoice("A"))
        assertNull(day.copy(groupChoices = mapOf("A" to choice.copy(transitionRest = 0))).validGroupChoice("A"))
    }
}
