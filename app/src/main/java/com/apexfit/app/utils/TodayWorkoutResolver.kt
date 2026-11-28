package com.apexfit.app.utils

import com.apexfit.app.data.CalendarPlanSnapshot
import com.apexfit.app.data.FitnessDao
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.RescheduledWorkoutSnapshot
import com.apexfit.app.data.ScheduleMoveReservation
import com.apexfit.app.data.ScheduleRevisionSnapshot
import com.apexfit.app.data.TrainingSession
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

enum class TodayWorkoutKind { REGULAR, REST, DEFERRED, RESERVED, REVIEW }

data class TodayWorkoutChoice(val date: LocalDate, val session: PlanSession?,
                              val kind: TodayWorkoutKind, val reservation: ScheduleMoveReservation? = null)

object TodayWorkoutResolver {
    fun resolve(today: LocalDate, plans: List<CalendarPlanSnapshot>,
                revisions: List<ScheduleRevisionSnapshot>, reservations: List<ScheduleMoveReservation>,
                sessions: List<TrainingSession>, associations: List<RescheduledWorkoutSnapshot>,
                nowMs: Long = System.currentTimeMillis()): TodayWorkoutChoice {
        val plan = plans.singleOrNull()?.takeIf { snapshot ->
            snapshot.plan.isActive && snapshot.sessions.isNotEmpty() &&
                WorkoutSchedule.validationError(snapshot.plan, snapshot.sessions) == null &&
                snapshot.sessions.all { it.planId == snapshot.plan.id && WorkoutSchedule.weekday(it.day) != null } &&
                snapshot.sessions.map { it.cycleWeek to WorkoutSchedule.weekday(it.day) }.distinct().size == snapshot.sessions.size &&
                (snapshot.plan.cycleWeeks == 1 || snapshot.plan.cycleAnchorMonday != null)
        }
        val regular = plan?.let { WorkoutSchedule.sessionForDate(it.plan, it.sessions, today) }
            ?.takeIf(WorkoutSchedule::isTraining)
        val todayRows = reservations.filter { it.originalDate == today.toString() || it.targetDate == today.toString() }
        if (todayRows.isEmpty()) return TodayWorkoutChoice(today, regular,
            if (regular == null) TodayWorkoutKind.REST else TodayWorkoutKind.REGULAR)
        val row = todayRows.singleOrNull()
        fun review() = TodayWorkoutChoice(today, regular, TodayWorkoutKind.REVIEW, row)
        if (row == null || plan == null) return review()
        val move = ScheduleMoveStore.decode(row) ?: return review()
        val state = ScheduleMoveStore.state(row, revisions, today, nowMs)
        if (state == ReservationState.STALE || state == ReservationState.ELAPSED) return review()
        val source = ScheduleLedger.resolve(move.originalDate, revisions)
        val revision = revisions.singleOrNull { it.revision.id == move.revisionId } ?: return review()
        val chosen = plan.sessions.singleOrNull { it.id == move.slotId } ?: return review()
        val expectedSlot = WorkoutSchedule.sessionForDate(plan.plan, plan.sessions, move.originalDate)
        if (revision.revision.planId != plan.plan.id || chosen != expectedSlot ||
            !WorkoutSchedule.isTraining(chosen) || chosen.label != source.label ||
            sessions.any { it.date == row.originalDate && it.scheduleOccurrenceId == row.occurrenceId &&
                it.planSessionId == row.slotId && RescheduledWorkouts.coherent(it, nowMs) } ||
            associations.any { it.association.occurrenceId == row.occurrenceId }) return review()
        return when {
            move.originalDate == today && state == ReservationState.UPCOMING && regular == chosen ->
                TodayWorkoutChoice(today, null, TodayWorkoutKind.DEFERRED, row)
            move.targetDate == today && state == ReservationState.DUE && regular == null ->
                TodayWorkoutChoice(today, chosen, TodayWorkoutKind.RESERVED, row)
            else -> review()
        }
    }

    /** Both endpoints are queried even when the reservation spans months. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(dao: FitnessDao, today: LocalDate): Flow<TodayWorkoutChoice> {
        val date = today.toString()
        return dao.observeScheduleMoveReservations(date, date).flatMapLatest { rows ->
            val dates = rows.mapNotNull(ScheduleMoveStore::decode).flatMap { listOf(it.originalDate, it.targetDate) }
            val start = minOf(today, dates.minOrNull() ?: today).toString()
            val end = maxOf(today, dates.maxOrNull() ?: today).toString()
            combine(dao.observeCalendarPlans(), dao.observeScheduleRevisions(start, end),
                dao.observeCalendarSessions(start, end), dao.observeScheduleWorkoutAssociations(start, end)) {
                    plans, revisions, sessions, associations ->
                resolve(today, plans, revisions, rows, sessions, associations)
            }
        }
    }
}
