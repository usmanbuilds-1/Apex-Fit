package com.apexfit.app.utils

import com.apexfit.app.data.PlanExercise
import org.junit.Assert.*
import org.junit.Test

class PermanentWorkoutGroupsTest {
    private fun exercise(id: Long, key: String? = null, session: Long = 1,
                         transition: Int? = key?.let { 15 }, round: Int? = key?.let { 120 }) =
        PlanExercise(id = id, planSessionId = session, exerciseId = "same-catalog",
            name = "Exercise $id", muscleGroup = "Back", sets = 3, repsMin = 8,
            repsMax = 12, weight = 20.0, restSeconds = 90, notes = "",
            groupKey = key, groupTransitionRest = transition, groupRoundRest = round)

    @Test fun resolvesMultipleGroupsAfterStandaloneExerciseAndRepeatedCatalogIds() {
        val list = listOf(exercise(1), exercise(2, "A"), exercise(3, "A"),
            exercise(4), exercise(5, "B"), exercise(6, "B"), exercise(7, "B"))
        assertNull(PermanentWorkoutGroups.validationError(list))
        val groups = PermanentWorkoutGroups.resolve(list)
        assertEquals(listOf(PermanentWorkoutGroup("A", 1, 2, 15, 120),
            PermanentWorkoutGroup("B", 4, 3, 15, 120)), groups)
    }

    @Test fun rejectsSingletonSplitInconsistentAndMalformedGroups() {
        assertNotNull(PermanentWorkoutGroups.validationError(listOf(exercise(1, "A"))))
        assertNotNull(PermanentWorkoutGroups.validationError(listOf(exercise(1, "A"), exercise(2), exercise(3, "A"))))
        assertNotNull(PermanentWorkoutGroups.validationError(listOf(exercise(1, "A"), exercise(2, "A", round = 90))))
        assertNotNull(PermanentWorkoutGroups.validationError(listOf(exercise(1, transition = 15))))
        assertNotNull(PermanentWorkoutGroups.validationError(listOf(exercise(1, "A", transition = 0), exercise(2, "A", transition = 0))))
        assertNotNull(PermanentWorkoutGroups.validationError(listOf(exercise(1, "A"), exercise(2, "A", session = 2))))
    }

    @Test fun legacyNotesNeverActivateGroupAndKeyScopeIsPerSession() {
        val legacy = exercise(1).copy(notes = "Imported group: A")
        assertTrue(PermanentWorkoutGroups.resolve(listOf(legacy)).isEmpty())
        assertNull(PermanentWorkoutGroups.validationError(listOf(exercise(1, "A"), exercise(2, "A"),
            exercise(3, "A", session = 2), exercise(4, "A", session = 2))))
    }

    @Test fun planEditorCreatesResizesAndRemovesWithoutChangingPrescriptionsOrOrder() {
        val original = listOf(exercise(1), exercise(2), exercise(3), exercise(4, "B"), exercise(5, "B"))
        val ids = original.map { it.id }
        val grouped = requireNotNull(PermanentWorkoutGroups.configure(original, 1, ids, "A",
            listOf(1L, 2L), 20, 120))
        assertEquals(listOf("A", "A", null, "B", "B"), grouped.map { it.groupKey })
        val resized = requireNotNull(PermanentWorkoutGroups.configure(grouped, 1, ids, "A",
            listOf(2L, 3L), 30, 90))
        assertEquals(listOf(null, "A", "A", "B", "B"), resized.map { it.groupKey })
        assertEquals(original, PermanentWorkoutGroups.remove(resized, 1, "A"))
        assertEquals(ids, resized.map { it.id })
        assertEquals(original.map { it.sets }, resized.map { it.sets })
    }

    @Test fun planEditorRejectsStaleOrderOverlapAndNonconsecutiveSelection() {
        val rows = listOf(exercise(1), exercise(2), exercise(3, "B"), exercise(4, "B"))
        val ids = rows.map { it.id }
        assertNull(PermanentWorkoutGroups.configure(rows, 1, ids.reversed(), "A", listOf(1L, 2L), 15, 90))
        assertNull(PermanentWorkoutGroups.configure(rows, 1, ids, "A", listOf(2L, 3L), 15, 90))
        assertNull(PermanentWorkoutGroups.configure(rows, 1, ids, "A", listOf(1L, 3L), 15, 90))
        assertNull(PermanentWorkoutGroups.configure(rows, 1, ids, "A", listOf(1L, 2L), 0, 90))
    }

    @Test fun deletingMemberDissolvesSingletonButPreservesLargerGroup() {
        val rows = listOf(exercise(1, "A"), exercise(2, "A"), exercise(3, "B"), exercise(4, "B"), exercise(5, "B"))
        val after = PermanentWorkoutGroups.deleteOccurrence(rows, 1)
        assertNull(after.first().groupKey)
        assertNull(after.first().groupRoundRest)
        val larger = PermanentWorkoutGroups.deleteOccurrence(after, 4)
        assertEquals(listOf("B", "B"), larger.drop(1).map { it.groupKey })
        assertNull(PermanentWorkoutGroups.validationError(larger))
    }
}
