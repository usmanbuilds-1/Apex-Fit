package com.apexfit.app.utils

import com.apexfit.app.data.ActiveSession
import com.apexfit.app.data.ScheduleWorkoutAssociation
import com.apexfit.app.data.TrainingSession
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ReservedWorkoutRecoveryTest {
    private val occurrence = "1:2026-11-30:12"
    private val draft = ActiveSession("12", "Upper", startTime = 300, exercises = mutableListOf(),
        startedLocalDate = "2026-12-01", reservedOccurrenceId = occurrence, reservationRecordedAt = 200)
    private val workout = TrainingSession(draft.sessionId, "2026-12-01", "Upper", true, 30, 3,
        planSessionId = 12, startedAt = 300, completedAt = 400)
    private val association = ScheduleWorkoutAssociation(occurrence, "2026-11-30", workout.id, 400)

    @Test fun durableDraftRetainsExactReservationIdentityOnDecode() {
        val decoded = ActiveWorkoutCodec.decode(Gson().toJson(draft))
        assertEquals(draft, decoded.session)
        assertFalse(decoded.migrated)
    }

    @Test fun oldDraftCannotInventReservationIdentity() {
        val raw = Gson().toJson(draft.copy(schemaVersion = 7))
        val decoded = ActiveWorkoutCodec.decode(raw)
        assertTrue(decoded.migrated)
        assertNull(decoded.session.reservedOccurrenceId)
        assertNull(decoded.session.reservationRecordedAt)
    }

    @Test fun malformedOrConflictingDraftIsRejected() {
        assertTrue(runCatching { ActiveWorkoutCodec.decode(Gson().toJson(
            draft.copy(reservationRecordedAt = 301))) }.isFailure)
        assertTrue(runCatching { ActiveWorkoutCodec.decode(Gson().toJson(
            draft.copy(reservedOccurrenceId = "1:2026-11-30:99"))) }.isFailure)
        assertTrue(runCatching { ActiveWorkoutCodec.decode(Gson().toJson(
            draft.copy(scheduleOccurrenceId = "1:2026-12-01:12"))) }.isFailure)
    }

    @Test fun recoveryRequiresCommittedMatchingAssociationNotJournalIntent() {
        assertNull(ReservedWorkoutExecution.receiptCredit(workout, null, association))
        assertEquals(false, ReservedWorkoutExecution.receiptCredit(workout, occurrence, null))
        assertEquals(true, ReservedWorkoutExecution.receiptCredit(workout, occurrence, association))
        assertEquals(false, ReservedWorkoutExecution.receiptCredit(workout, occurrence, association.copy(sessionId = "other")))
        assertEquals(false, ReservedWorkoutExecution.receiptCredit(workout, occurrence, association.copy(occurrenceId = "2:2026-11-30:12")))
        assertEquals(false, ReservedWorkoutExecution.receiptCredit(workout, occurrence, association.copy(recordedAt = 399)))
        assertEquals(true, ReservedWorkoutExecution.receiptCredit(
            workout.copy(completed = false, status = "partial"), occurrence, association))
        assertEquals(false, ReservedWorkoutExecution.receiptCredit(
            workout.copy(completed = false, status = "completed"), occurrence, association))
    }
}
