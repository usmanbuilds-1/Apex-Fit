package com.apexfit.app.utils

import com.apexfit.app.data.CalendarPlanSnapshot
import com.apexfit.app.data.ScheduleRevision
import com.apexfit.app.data.ScheduleRevisionSlot
import com.apexfit.app.data.ScheduleRevisionSnapshot
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.TrainingSession
import com.apexfit.app.data.WorkoutPlan
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class TrainingCalendarTest {
    private val today = LocalDate.parse("2026-11-16")
    private val month = YearMonth.from(today)
    private val plan = WorkoutPlan(id = 1, name = "Rotation", goal = "Build muscle",
        isActive = true, cycleWeeks = 2, cycleAnchorMonday = "2026-11-16")
    private val slots = listOf(
        PlanSession(1, 1, "Upper A", "Monday", "Chest", 1),
        PlanSession(2, 1, "Lower B", "Monday", "Quads", 2),
        PlanSession(3, 1, "Rest", "Tuesday", "Muscle Recovery & Rest", 1)
    )
    private fun session(id: String, date: String, completed: Boolean = true, status: String = "completed",
                        timestamp: Long? = null) =
        TrainingSession(id, date, "Workout", completed, 30, 3, status = status, completedAt = timestamp)

    @Test fun rotationUsesCalendarWeeksNotCompletedWorkouts() {
        val result = TrainingCalendar.build(month, today, emptyList(), listOf(CalendarPlanSnapshot(plan, slots)))
        assertEquals("Upper A", result.days[15].plannedLabel)
        assertEquals("Lower B", result.days[22].plannedLabel)
        assertEquals(CalendarScheduleKind.REST, result.days[16].scheduleKind)
        assertEquals(CalendarScheduleKind.REST, result.days[17].scheduleKind)
        assertEquals(CalendarScheduleKind.UNKNOWN, result.days[8].scheduleKind)
    }

    @Test fun multipleSameDaySessionsPreservedButCompletedDayCountIsDistinct() {
        val records = listOf(session("a", "2026-11-12"), session("b", "2026-11-12"),
            session("c", "2026-11-12", false, "partial"),
            session("d", "2026-11-13", false, "partial"), session("a", "2026-11-12"))
        val result = TrainingCalendar.build(month, today, records, emptyList())
        assertEquals(1, result.completedDays)
        assertEquals(2, result.completedSessions)
        assertEquals(2, result.partialSessions)
        assertEquals(1, result.partialOnlyDays)
        assertEquals(3, result.days[11].sessions.size)
        assertEquals(CalendarScheduleKind.UNKNOWN, result.days[11].scheduleKind)
    }

    @Test fun excludesFutureCancelledInvalidAndOutOfRangeSessions() {
        val records = listOf(session("a", "2026-11-17"), session("b", "2026-11-10", false, "cancelled"),
            session("c", "bad"), session("d", "2026-10-31"),
            session("e", "2026-11-12", timestamp = Long.MAX_VALUE))
        val result = TrainingCalendar.build(month, today, records, emptyList())
        assertEquals(0, result.completedSessions)
        assertEquals(0, result.partialSessions)
    }

    @Test fun invalidOrAmbiguousActivePlanDoesNotInventRestDays() {
        val valid = CalendarPlanSnapshot(plan, slots)
        val bad = listOf(
            emptyList(),
            listOf(valid, valid),
            listOf(CalendarPlanSnapshot(plan.copy(cycleAnchorMonday = null), slots)),
            listOf(CalendarPlanSnapshot(plan, slots + slots.first().copy(id = 9))),
            listOf(CalendarPlanSnapshot(plan, emptyList()))
        )
        bad.forEach { active ->
            assertTrue(TrainingCalendar.build(month, today, emptyList(), active).days.all {
                it.scheduleKind == CalendarScheduleKind.UNKNOWN
            })
        }
    }

    @Test fun planChangesNeverRewritePreviousCalendarDates() {
        val saved = listOf(session("a", "2026-11-09"))
        val before = TrainingCalendar.build(month, today, saved, listOf(CalendarPlanSnapshot(plan, slots)))
        val after = TrainingCalendar.build(month, today, saved, listOf(CalendarPlanSnapshot(plan,
            slots.map { it.copy(label = "New plan") })))
        assertEquals(before.days.take(15), after.days.take(15))
        assertNotEquals(before.days[15].plannedLabel, after.days[15].plannedLabel)
    }

    @Test fun pastUsesRecordedRotationAndNeverMatchesUnlinkedSavedWorkout() {
        val revision = ScheduleRevisionSnapshot(
            ScheduleRevision(7, "2026-11-02", 100, 42, "Old rotation", 2, "2026-11-02"),
            listOf(ScheduleRevisionSlot(7, 101, 1, 1, "Old A", true),
                ScheduleRevisionSlot(7, 102, 2, 1, "Old B", true)))
        val replacement = ScheduleRevisionSnapshot(
            ScheduleRevision(8, "2026-11-11", 200, 43, "New plan", 1, null),
            listOf(ScheduleRevisionSlot(8, 201, 1, 5, "Friday", true)))
        val result = TrainingCalendar.build(month, today,
            listOf(session("unlinked", "2026-11-09")), listOf(CalendarPlanSnapshot(plan, slots)),
            revisions = listOf(revision, replacement))
        assertEquals(CalendarScheduleSource.UNKNOWN, result.days[0].scheduleSource)
        assertEquals("Old A", result.days[1].plannedLabel)
        val oldB = result.days[8]
        assertEquals("Old B", oldB.plannedLabel)
        assertEquals(CalendarScheduleSource.RECORDED, oldB.scheduleSource)
        assertEquals("7:2026-11-09:102", oldB.recordedOccurrenceId)
        assertEquals(1, oldB.completedCount)
        assertEquals(CalendarScheduleKind.REST, result.days[9].scheduleKind)
        assertEquals("Friday", result.days[12].plannedLabel)
        assertEquals(CalendarLogKind.NONE, result.days[12].logKind)
        assertEquals(CalendarScheduleSource.LIVE_PROJECTION, result.days[15].scheduleSource)
        assertNull(result.days[15].recordedOccurrenceId)
    }

    @Test fun leapYearAndDecemberBoundaryUseRealCalendarDates() {
        val leap = TrainingCalendar.build(YearMonth.of(2024, 2), today, emptyList(), emptyList())
        assertEquals(LocalDate.parse("2024-02-29"), leap.days.last().date)
        val jan = TrainingCalendar.build(YearMonth.of(2027, 1), today, emptyList(), listOf(CalendarPlanSnapshot(plan, slots)))
        assertEquals(31, jan.days.size)
        assertEquals("Lower B", jan.days[3].plannedLabel)
    }
}
