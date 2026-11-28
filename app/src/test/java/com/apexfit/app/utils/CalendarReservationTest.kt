package com.apexfit.app.utils

import com.apexfit.app.data.*
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CalendarReservationTest {
    private val today = LocalDate.parse("2026-11-29")
    private val original = today.plusDays(1)
    private val target = original.plusDays(1)
    private val plan = WorkoutPlan(id = 9, name = "Upper", goal = "Strength", isActive = true)
    private val slot = PlanSession(12, 9, "Upper", "Monday", "Upper", 1)
    private val revision = ScheduleRevisionSnapshot(
        ScheduleRevision(1, original.toString(), 100, 9, "Upper", 1, null),
        listOf(ScheduleRevisionSlot(1, 12, 1, 1, "Upper", true)))
    private val reservation = ScheduleMoveReservation("1:$original:12", original.toString(),
        target.toString(), 1, 12, 200)
    private val plans = listOf(CalendarPlanSnapshot(plan, listOf(slot)))

    @Test fun sourceAndDestinationShownAcrossMonthsWithoutChangingScheduledEvidence() {
        fun month(date: LocalDate, rows: List<ScheduleMoveReservation> = listOf(reservation)) =
            TrainingCalendar.build(YearMonth.from(date), today, emptyList(), plans, nowMs = 300,
                revisions = listOf(revision), reservations = rows)
        val source = month(original).days.last()
        val destination = month(target).days.first()
        assertTrue(source.reservations.single().isOriginal)
        assertFalse(destination.reservations.single().isOriginal)
        assertEquals(ReservationState.UPCOMING, source.reservations.single().state)
        assertEquals(CalendarScheduleKind.TRAINING, source.scheduleKind)
        assertEquals(CalendarScheduleKind.REST, destination.scheduleKind)
        assertEquals(RecordedOccurrenceStatus.NOT_APPLICABLE, source.occurrenceStatus)
        assertNull(source.futureMoveOccurrenceId)
        assertEquals(reservation.occurrenceId, month(original, emptyList()).days.last().futureMoveOccurrenceId)
    }

    @Test fun staleReservationCannotRewriteHistoricalOutcome() {
        val replacement = revision.copy(revision = revision.revision.copy(id = 2, effectiveFrom = target.toString()),
            slots = revision.slots.map { it.copy(revisionId = 2) })
        val result = TrainingCalendar.build(YearMonth.from(original), target.plusDays(1), emptyList(), plans,
            nowMs = 300, revisions = listOf(revision, replacement), reservations = listOf(reservation))
        val source = result.days.last()
        assertEquals(ReservationState.STALE, source.reservations.single().state)
        assertEquals(RecordedOccurrenceStatus.UNRESOLVED, source.occurrenceStatus)
        assertEquals(0, result.recordedCompletedOccurrences)
    }
}
