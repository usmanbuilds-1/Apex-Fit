package com.apexfit.app.utils

import com.apexfit.app.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutReportsTest {
    private val today = LocalDate.parse("2026-11-16")
    private val session = TrainingSession("s1", today.toString(), "Upper", false, 35, 3, status = "partial")
    private fun set(id: Long, kg: Double = 20.0, reps: Int = 8, occurrence: Int? = 1,
                    warmup: Boolean = false, completed: Boolean = true, unit: String = "kg") =
        ExerciseSet(id = id, sessionId = session.id, exerciseId = "press", exerciseName = "Press",
            muscleGroup = "Chest", weight = kg, reps = reps, rpe = 8, isWarmup = warmup,
            completed = completed, weightUnit = unit, occurrenceIndex = occurrence)
    private fun confirmation(s: ExerciseSet, basis: RecordedLoadBasis = RecordedLoadBasis.TOTAL,
                             setup: String = "gym a") =
        SetLoadConfirmation(s.id, LoadConfirmations.fingerprint(s), basis, setup)
    private fun fact(s: ExerciseSet, date: String = today.toString()) =
        AnalyticsWorkingSet(s, date, "Upper", false, "partial", null, null, emptyList())

    @Test fun preservesOccurrencesAndCountsOnlyEligibleWork() {
        val a = set(1)
        val b = set(2, reps = 5, occurrence = 2)
        val report = WorkoutReports.calculate(SavedWorkoutFacts(session, listOf(a, a, b,
            set(3, warmup = true), set(4, completed = false), set(5, kg = Double.NaN),
            set(6, kg = 0.0, reps = 10, occurrence = null), set(7).copy(sessionId = "other"))),
            emptyList(), emptyMap(), today)
        assertEquals(3, report.workingSets)
        assertEquals(23L, report.workingReps)
        assertEquals(1, report.warmups)
        assertEquals(1, report.incomplete)
        assertEquals(1, report.invalid)
        assertEquals(3, report.exercises.size)
        assertTrue(report.legacyGrouping)
        assertEquals(35, report.durationMinutes)
        assertFalse(report.session.completed)
    }

    @Test fun confirmedVolumeIsSeparatedByBasisAndSetup() {
        val a = set(1, kg = 20.0)
        val b = set(2, kg = 20.0 * AppConstants.KG_TO_LBS, unit = "lbs")
        val c = set(3, kg = 10.0)
        val d = set(4, kg = 30.0)
        val e = set(5, kg = 50.0)
        val confirmations = listOf(confirmation(a), confirmation(b),
            confirmation(c, RecordedLoadBasis.PER_HAND), confirmation(d, setup = "gym b"))
            .associateBy { it.setId }
        val report = WorkoutReports.calculate(SavedWorkoutFacts(session, listOf(a,b,c,d,e)),
            emptyList(), confirmations, today)
        assertEquals(3, report.loadGroups.size)
        assertEquals(320.0, report.loadGroups.single { it.basis == RecordedLoadBasis.TOTAL && it.setup == "gym a" }.kgReps, 1e-8)
        assertEquals(1, report.unconfirmedWorkingSets)
    }

    @Test fun correctionInvalidatesConfirmationAndRecordsRequireSourceSession() {
        val a = set(1, kg = 25.0)
        val older = set(2, kg = 20.0).copy(sessionId = "old")
        val confirmations = listOf(confirmation(a), confirmation(older)).associateBy { it.setId }
        val history = listOf(fact(older, "2026-11-15"), fact(a))
        val report = WorkoutReports.calculate(SavedWorkoutFacts(session, listOf(a)), history, confirmations, today)
        assertEquals(listOf(1L), report.records.map { it.setId })
        val edited = a.copy(reps = 9)
        val changed = WorkoutReports.calculate(SavedWorkoutFacts(session, listOf(edited)),
            listOf(fact(edited)), confirmations, today)
        assertTrue(changed.records.isEmpty())
        assertTrue(changed.loadGroups.isEmpty())
    }

    @Test fun emptyWorkoutHasUnknownDurationAndNoFabricatedVolume() {
        val result = WorkoutReports.calculate(SavedWorkoutFacts(session.copy(durationMinutes = 0), emptyList()),
            emptyList(), emptyMap(), today)
        assertNull(result.durationMinutes)
        assertEquals(0L, result.workingReps)
        assertTrue(result.loadGroups.isEmpty())
    }
}
