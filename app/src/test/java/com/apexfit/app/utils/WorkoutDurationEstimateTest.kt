package com.apexfit.app.utils

import com.apexfit.app.data.PlanExercise
import org.junit.Assert.*
import org.junit.Test

class WorkoutDurationEstimateTest {
    private fun ex(id: Long, sets: Int, key: String? = null, transition: Int = 15, round: Int = 120) =
        PlanExercise(id = id, planSessionId = 1, exerciseId = "ex$id", name = "Exercise $id",
            muscleGroup = "Back", sets = sets, repsMin = 10, repsMax = 10, weight = 20.0,
            restSeconds = 90, notes = "", groupKey = key,
            groupTransitionRest = transition.takeIf { key != null }, groupRoundRest = round.takeIf { key != null })

    @Test fun unequalSupersetUsesTwoTransitionsTwoRoundRestsAndNoTerminalRest() {
        val result = WorkoutDurationEstimate.estimate(listOf(ex(1,3,"A"), ex(2,2,"A")))
        assertTrue(result.groupingValid)
        assertEquals(150.0, result.workingSeconds, 1e-6)
        assertEquals(270L, result.restSeconds)
        assertEquals(7, result.minutes)
    }

    @Test fun groupExitAndStandaloneRestsCountOnlyWhenMoreWorkFollows() {
        val rows = listOf(ex(1,1), ex(2,3,"A"), ex(3,2,"A"), ex(4,1),
            ex(5,2,"B",20,90), ex(6,1,"B",20,90), ex(7,2,"B",20,90))
        val result = WorkoutDurationEstimate.estimate(rows)
        assertEquals(360.0, result.workingSeconds, 1e-6)
        // A: 2 transitions + 3 round rests; B: 3 transitions + 1 round rest.
        // Two standalone exercises have further work after them.
        assertEquals(720L, result.restSeconds)
        assertEquals(18, result.minutes)
    }

    @Test fun noRestAfterFinalWorkingSetAndRoundOnceForWholeWorkout() {
        assertEquals(0L, WorkoutDurationEstimate.estimate(listOf(ex(1,1))).restSeconds)
        val single = WorkoutDurationEstimate.estimate(listOf(ex(1,1), ex(2,1)))
        assertEquals(90L, single.restSeconds)
        assertEquals(3, single.minutes)
        assertEquals(0, WorkoutDurationEstimate.estimate(emptyList()).minutes)
    }

    @Test fun invalidGroupFallsBackWithoutInventingTransitions() {
        val result = WorkoutDurationEstimate.estimate(listOf(ex(1,1,"A"), ex(2,1), ex(3,1,"A")))
        assertFalse(result.groupingValid)
        assertEquals(180L, result.restSeconds)
    }
}
