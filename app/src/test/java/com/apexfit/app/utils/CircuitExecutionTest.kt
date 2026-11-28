package com.apexfit.app.utils

import com.apexfit.app.data.ActiveCircuit
import com.apexfit.app.data.ActiveExercise
import com.apexfit.app.data.ActiveSession
import com.apexfit.app.data.ActiveSet
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class CircuitExecutionTest {
    private fun session(vararg counts: Int) = ActiveSession("1", "Test", exercises =
        counts.mapIndexed { index, count ->
            ActiveExercise("ex$index", "Exercise $index", "Chest",
                MutableList(count) { ActiveSet(it + 1, 20.0, 8) })
        }.toMutableList(), cursorIndex = 0)
    private fun configure(s: ActiveSession, count: Int = s.exercises.size) =
        CircuitExecution.configure(s, ActiveCircuit(count, 15, 90))!!
    private fun finishOne(s: ActiveSession): ActiveSession {
        val index = WorkoutResumeState.cursor(s)
        val ex = s.exercises[index]
        val i = ex.sets.indexOfFirst { !it.completed && !it.isWarmup }
        val sets = ex.sets.toMutableList()
        sets[i] = sets[i].copy(completed = true, completedAt = 1234L)
        val updated = s.copy(exercises = s.exercises.toMutableList().apply { set(index, ex.copy(sets = sets)) })
        return WorkoutResumeState.advance(updated, index, false)
    }

    @Test fun unevenRoundsAndRestIntervals() {
        var s = configure(session(3, 2))
        val sequence = mutableListOf<Int>()
        val rests = mutableListOf<Int?>()
        repeat(5) {
            val index = WorkoutResumeState.cursor(s)
            sequence.add(index)
            s = finishOne(s)
            rests.add(CircuitExecution.restAfter(s, index, WorkoutResumeState.cursor(s)))
        }
        assertEquals(listOf(0,1,0,1,0), sequence)
        assertEquals(listOf(15,90,15,90,null), rests)
        assertTrue(CircuitExecution.terminal(s, WorkoutResumeState.cursor(s)))
        assertEquals(5, s.exercises.sumOf { ex -> ex.sets.count { it.completed } })
    }

    @Test fun threeMembersSkipExhaustedAndContinueToOrdinaryExercise() {
        var s = configure(session(2, 1, 2, 3), 3)
        s = finishOne(s)
        assertEquals(1, WorkoutResumeState.cursor(s))
        s = WorkoutResumeState.advance(s, 1, true)
        assertEquals(2, WorkoutResumeState.cursor(s))
        assertFalse(s.exercises[1].sets.single().completed)
        assertEquals(listOf(1), s.skippedOccurrenceIndices)
        s = finishOne(s)
        assertEquals(0, WorkoutResumeState.cursor(s))
        s = finishOne(s)
        assertEquals(2, WorkoutResumeState.cursor(s))
        s = finishOne(s)
        assertEquals(3, WorkoutResumeState.cursor(s))
        assertFalse(CircuitExecution.terminal(s, 2))
    }

    @Test fun resumeRoundTripPreservesIdentityCursorAndSavedWork() {
        val original = configure(session(3,2))
        val after = finishOne(original)
        val restored = ActiveWorkoutCodec.decode(Gson().toJson(after)).session
        assertEquals(original.sessionId, restored.sessionId)
        assertEquals(after.circuit, restored.circuit)
        assertEquals(1, WorkoutResumeState.cursor(restored))
        assertTrue(restored.exercises[0].sets[0].completed)
        assertEquals(0, WorkoutResumeState.cursor(finishOne(restored)))
    }

    @Test fun legacyV2MigrationKeepsStableIdAndNoCircuit() {
        val old = session(2,2).copy(schemaVersion = 2)
        val migrated = ActiveWorkoutCodec.decode(Gson().toJson(old))
        assertTrue(migrated.migrated)
        assertFalse(migrated.legacyIdentity)
        assertEquals(old.sessionId, migrated.session.sessionId)
        assertNull(migrated.session.circuit)
        assertEquals(ActiveSession.CURRENT_VERSION, migrated.session.schemaVersion)
    }

    @Test fun noRetroactiveGroupingOrInvalidRest() {
        val s = session(2,2)
        assertNull(CircuitExecution.configure(s, ActiveCircuit(1, 15,90)))
        assertNull(CircuitExecution.configure(s, ActiveCircuit(2, 0,90)))
        assertNull(CircuitExecution.configure(s, ActiveCircuit(2, 15,3601)))
        assertNull(CircuitExecution.configure(finishOne(configure(s)), null))
        assertTrue(runCatching { ActiveWorkoutCodec.decode(Gson().toJson(s.copy(circuit = ActiveCircuit(3, 15,90)))) }.isFailure)
    }

    @Test fun ordinarySequentialAdvanceUnchanged() {
        val s = session(2,2)
        assertEquals(1, WorkoutResumeState.cursor(WorkoutResumeState.advance(s,0,false)))
        assertNull(s.circuit)
    }
}
