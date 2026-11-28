package com.apexfit.app.utils

import com.apexfit.app.data.ActiveExercise
import com.apexfit.app.data.ActiveSession
import com.apexfit.app.data.ActiveSet
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.SavedWorkoutFacts
import com.apexfit.app.data.TrainingSession
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AdvancedSetsTest {
    private val drop = AdvancedSetDetails(AdvancedSetKind.DROP, SetOutcome.FAILURE,
        listOf(SetContinuation(15.0, 6, 0, SetOutcome.UNKNOWN),
            SetContinuation(10.0, 5, 5, SetOutcome.NOT_FAILURE)))

    @Test fun roundTripPreservesActualLoadsRepsRestAndOutcomes() {
        assertEquals(drop, AdvancedSets.decode(20.0, AdvancedSets.encode(20.0, drop)))
        assertNull(AdvancedSets.decode(20.0, "v99|DROP|FAILURE|"))
        assertNull(AdvancedSets.decode(20.0, null))
    }

    @Test fun validatesDropAndRestPauseWithoutInventingFailure() {
        assertFalse(AdvancedSets.valid(15.0, drop))
        assertFalse(AdvancedSets.valid(20.0, drop.copy(continuations = emptyList())))
        assertFalse(AdvancedSets.valid(20.0, drop.copy(continuations = listOf(SetContinuation(10.0, 0, 0, SetOutcome.UNKNOWN)))))
        val pause = AdvancedSetDetails(AdvancedSetKind.REST_PAUSE, SetOutcome.UNKNOWN,
            listOf(SetContinuation(20.0, 3, 20, SetOutcome.UNKNOWN)))
        assertTrue(AdvancedSets.valid(20.0, pause))
        assertFalse(AdvancedSets.valid(19.0, pause))
        assertFalse(AdvancedSets.valid(20.0, pause.copy(continuations = listOf(SetContinuation(20.0, 3, 0, SetOutcome.UNKNOWN)))))
        assertTrue(AdvancedSets.valid(20.0, AdvancedSetDetails(AdvancedSetKind.WORKING, SetOutcome.NOT_FAILURE, emptyList())))
    }

    @Test fun reportCountsOneBoutAndActualRepetitionsButNoUnconfirmedVolume() {
        val date = LocalDate.parse("2026-11-28")
        val session = TrainingSession("s", date.toString(), "Upper", true, 30, 3)
        val parent = ExerciseSet(id = 1, sessionId = "s", exerciseId = "curl", exerciseName = "Curl",
            muscleGroup = "Biceps", weight = 20.0, reps = 10, rpe = 0, isWarmup = false,
            completed = true, advancedDetails = AdvancedSets.encode(20.0, drop))
        val report = WorkoutReports.calculate(SavedWorkoutFacts(session, listOf(parent)), emptyList(), emptyMap(), date)
        assertEquals(1, report.workingSets)
        assertEquals(21L, report.workingReps)
        assertEquals(21L, report.exercises.single().reps)
        assertTrue(report.loadGroups.isEmpty())
        assertEquals(1, report.unconfirmedWorkingSets)
        assertTrue(TrainingEvidence.isWorkingSet(parent))
        assertFalse(TrainingEvidence.isWorkingSet(parent.copy(isWarmup = true)))
    }

    @Test fun activeDraftRoundTripAndV3UpgradePreserveIdentity() {
        val parent = ActiveSet(1, 20.0, 10, completed = true,
            advancedDetails = AdvancedSets.encode(20.0, drop))
        val session = ActiveSession("1", "Upper", exercises = mutableListOf(
            ActiveExercise("curl", "Curl", "Biceps", mutableListOf(parent))))
        val restored = ActiveWorkoutCodec.decode(Gson().toJson(session)).session
        assertEquals(session.sessionId, restored.sessionId)
        assertEquals(parent.advancedDetails, restored.exercises.single().sets.single().advancedDetails)
        val older = session.copy(schemaVersion = 3, exercises = mutableListOf(
            session.exercises.single().copy(sets = mutableListOf(parent.copy(advancedDetails = null)))))
        val migrated = ActiveWorkoutCodec.decode(Gson().toJson(older))
        assertTrue(migrated.migrated)
        assertEquals(older.sessionId, migrated.session.sessionId)
        assertEquals(ActiveSession.CURRENT_VERSION, migrated.session.schemaVersion)
    }

    @Test fun corruptPayloadCannotBeRestoredAsOrdinarySet() {
        val bad = ActiveSet(1, 20.0, 10, completed = true, advancedDetails = "bad")
        val session = ActiveSession("1", "Upper", exercises = mutableListOf(
            ActiveExercise("curl", "Curl", "Biceps", mutableListOf(bad))))
        assertTrue(runCatching { ActiveWorkoutCodec.decode(Gson().toJson(session)) }.isFailure)
    }
}
