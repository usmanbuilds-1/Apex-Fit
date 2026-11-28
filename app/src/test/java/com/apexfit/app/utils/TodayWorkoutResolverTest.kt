package com.apexfit.app.utils

import com.apexfit.app.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TodayWorkoutResolverTest {
    private val original = LocalDate.parse("2026-11-30")
    private val target = original.plusDays(1)
    private val plan = WorkoutPlan(id = 9, name = "Upper/Lower", goal = "Strength", isActive = true)
    private val upper = PlanSession(12, 9, "Upper", "Monday", "Upper", 1)
    private val lower = PlanSession(13, 9, "Lower", "Wednesday", "Lower", 1)
    private val plans = listOf(CalendarPlanSnapshot(plan, listOf(upper, lower)))
    private val revision = ScheduleRevisionSnapshot(
        ScheduleRevision(1, original.toString(), 100, 9, "Upper/Lower", 1, null),
        listOf(ScheduleRevisionSlot(1, 12, 1, 1, "Upper", true),
            ScheduleRevisionSlot(1, 13, 1, 3, "Lower", true)))
    private val row = ScheduleMoveReservation("1:$original:12", original.toString(),
        target.toString(), 1, 12, 200)

    @Test fun originalDeferredAndDestinationUsesExactSlot() {
        fun resolve(date: LocalDate, rows: List<ScheduleMoveReservation>) =
            TodayWorkoutResolver.resolve(date, plans, listOf(revision), rows, emptyList(), emptyList(), 300)
        assertEquals(TodayWorkoutKind.REGULAR, resolve(original, emptyList()).kind)
        assertEquals(TodayWorkoutKind.REST, resolve(target, emptyList()).kind)
        val deferred = resolve(original, listOf(row))
        assertEquals(TodayWorkoutKind.DEFERRED, deferred.kind)
        assertNull(deferred.session)
        val due = resolve(target, listOf(row))
        assertEquals(TodayWorkoutKind.RESERVED, due.kind)
        assertEquals(upper, due.session)
        assertEquals(row, due.reservation)
        assertEquals(lower, resolve(target.plusDays(1), listOf(row)).session)
    }

    @Test fun changedRevisionOrCompletedOriginalDoesNotLaunchReservedSlot() {
        val replacement = revision.copy(revision = revision.revision.copy(id = 2, effectiveFrom = target.toString()),
            slots = revision.slots.map { it.copy(revisionId = 2) })
        val stale = TodayWorkoutResolver.resolve(target, plans, listOf(revision, replacement),
            listOf(row), emptyList(), emptyList(), 300)
        assertEquals(TodayWorkoutKind.REVIEW, stale.kind)
        assertNull(stale.session)
        val workout = TrainingSession("saved", original.toString(), "Upper", true, 30, 3,
            planSessionId = 12, startedAt = 201, completedAt = 250, scheduleOccurrenceId = row.occurrenceId)
        val alreadyDone = TodayWorkoutResolver.resolve(target, plans, listOf(revision),
            listOf(row), listOf(workout), emptyList(), 300)
        assertEquals(TodayWorkoutKind.REVIEW, alreadyDone.kind)
        assertNull(alreadyDone.session)
        val changedLive = plans.map { it.copy(sessions = it.sessions.map { s ->
            if (s.id == upper.id) s.copy(label = "New Upper") else s }) }
        val replaced = TodayWorkoutResolver.resolve(target, changedLive, listOf(revision),
            listOf(row), emptyList(), emptyList(), 300)
        assertEquals(TodayWorkoutKind.REVIEW, replaced.kind)
    }
}
