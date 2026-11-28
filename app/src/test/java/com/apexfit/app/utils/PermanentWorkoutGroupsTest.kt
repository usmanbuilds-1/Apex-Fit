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
}
