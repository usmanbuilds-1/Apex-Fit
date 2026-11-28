package com.apexfit.app.utils

import com.apexfit.app.data.ActiveCircuit
import com.apexfit.app.data.ActiveExercise
import com.apexfit.app.data.ActiveSession
import com.apexfit.app.data.ActiveSet
import com.apexfit.app.data.ActiveWorkoutGroup
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class PermanentGroupExecutionTest {
    private val groups = listOf(
        ActiveWorkoutGroup("A", 1, 2, 15, 120),
        ActiveWorkoutGroup("B", 4, 3, 20, 90)
    )

    private fun session() = ActiveSession("1", "Grouped", exercises =
        listOf(1, 3, 2, 1, 2, 1, 2).mapIndexed { index, count ->
            ActiveExercise("exercise-$index", "Exercise $index", "Back",
                MutableList(count) { ActiveSet(it + 1, 20.0, 8) })
        }.toMutableList(), cursorIndex = 0, permanentGroups = groups)

    private fun finishOne(s: ActiveSession): ActiveSession {
        val index = WorkoutResumeState.cursor(s)
        val exercise = s.exercises[index]
        val setIndex = exercise.sets.indexOfFirst { !it.completed && !it.isWarmup }
        val sets = exercise.sets.toMutableList()
        sets[setIndex] = sets[setIndex].copy(completed = true, completedAt = 1234L)
        val updated = s.copy(exercises = s.exercises.toMutableList().apply {
            set(index, exercise.copy(sets = sets))
        })
        return if (CircuitExecution.contains(updated, index) || sets.none { !it.completed && !it.isWarmup })
            WorkoutResumeState.advance(updated, index, false) else updated
    }

    @Test fun disjointGroupsRotateAndReturnToSequentialExercisesWithoutExtraSets() {
        var state = session()
        assertTrue(CircuitExecution.valid(state))
        val order = mutableListOf<Int>()
        val rest = mutableListOf<Int?>()
        repeat(12) {
            val index = WorkoutResumeState.cursor(state)
            order += index
            state = finishOne(state)
            rest += if (CircuitExecution.contains(state, index))
                CircuitExecution.restAfter(state, index, WorkoutResumeState.cursor(state)) else null
        }
        assertEquals(listOf(0,1,2,1,2,1,3,4,5,6,4,6), order)
        assertEquals(listOf(null,15,120,15,120,120,null,20,20,90,20,null), rest)
        assertTrue(CircuitExecution.terminal(state, WorkoutResumeState.cursor(state)))
        assertEquals(12, state.exercises.sumOf { e -> e.sets.count { it.completed } })
    }

    @Test fun skipAndRestartKeepFrozenRangesAndDoNotMarkSkippedSetsCompleted() {
        var state = finishOne(session())
        state = finishOne(state)
        assertEquals(2, WorkoutResumeState.cursor(state))
        state = WorkoutResumeState.advance(state, 2, true)
        assertEquals(1, WorkoutResumeState.cursor(state))
        state = ActiveWorkoutCodec.decode(Gson().toJson(state)).session
        assertEquals(groups, state.permanentGroups)
        assertEquals(listOf(2), state.skippedOccurrenceIndices)
        assertFalse(state.exercises[2].sets.any { it.completed })
        state = finishOne(finishOne(state))
        assertEquals(3, WorkoutResumeState.cursor(state))
        assertFalse(CircuitExecution.canConfigure(state))
    }

    @Test fun rejectsOverlappingOrMalformedRangesAndMixedLegacyCircuit() {
        val state = session()
        listOf(
            groups + ActiveWorkoutGroup("C", 5, 2, 15, 90),
            listOf(groups.first().copy(memberCount = Int.MAX_VALUE)),
            listOf(groups.first().copy(firstIndex = -1)),
            listOf(groups.first().copy(transitionRestSeconds = 0)),
            listOf(groups.first(), groups.last().copy(key = "A")),
            groups.reversed()
        ).forEach { invalid ->
            assertFalse(CircuitExecution.valid(state.copy(permanentGroups = invalid)))
            assertTrue(runCatching { ActiveWorkoutCodec.decode(Gson().toJson(
                state.copy(permanentGroups = invalid))) }.isFailure)
        }
        assertFalse(CircuitExecution.valid(state.copy(circuit = ActiveCircuit(2, 15, 90))))
    }

    @Test fun versionEightCannotInventPermanentGroupingButRetainsLegacyCircuit() {
        val legacy = session().copy(schemaVersion = 8, circuit = ActiveCircuit(2, 15, 90))
        val restored = ActiveWorkoutCodec.decode(Gson().toJson(legacy)).session
        assertNull(restored.permanentGroups)
        assertEquals(legacy.circuit, restored.circuit)
        assertEquals(ActiveSession.CURRENT_VERSION, restored.schemaVersion)
        assertTrue(CircuitExecution.valid(restored))
    }
}
