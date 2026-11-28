package com.apexfit.app.utils

import com.apexfit.app.data.CalendarPlanSnapshot
import com.apexfit.app.data.TrainingSession
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarScheduleKind { TRAINING, REST, UNKNOWN }
enum class CalendarLogKind { COMPLETED, PARTIAL, NONE }

data class TrainingCalendarDay(
    val date: LocalDate,
    val sessions: List<TrainingSession>,
    val scheduleKind: CalendarScheduleKind,
    val plannedLabel: String?,
    val plannedSessionId: Long?,
    val planName: String?
) {
    val completedCount: Int get() = sessions.count { it.completed }
    val partialCount: Int get() = sessions.count { !it.completed && it.status == "partial" }
    val logKind: CalendarLogKind get() = when {
        completedCount > 0 -> CalendarLogKind.COMPLETED
        partialCount > 0 -> CalendarLogKind.PARTIAL
        else -> CalendarLogKind.NONE
    }
}

data class TrainingCalendarMonth(val month: YearMonth, val days: List<TrainingCalendarDay>) {
    val completedDays: Int get() = days.count { it.completedCount > 0 }
    val partialOnlyDays: Int get() = days.count { it.logKind == CalendarLogKind.PARTIAL }
    val completedSessions: Int get() = days.sumOf { it.completedCount }
    val partialSessions: Int get() = days.sumOf { it.partialCount }
}

object TrainingCalendar {
    fun build(month: YearMonth, today: LocalDate, sessions: List<TrainingSession>,
              activePlans: List<CalendarPlanSnapshot>, nowMs: Long = System.currentTimeMillis()): TrainingCalendarMonth {
        val logged = sessions.filter {
            val date = runCatching { LocalDate.parse(it.date) }.getOrNull()
            date != null && YearMonth.from(date) == month && date <= today &&
                (it.completed || it.status == "partial") &&
                (it.completedAt ?: it.startedAt ?: 0L) <= nowMs
        }.distinctBy { it.id }.groupBy { it.date }
        val snapshot = activePlans.singleOrNull()?.takeIf { candidate ->
            val plan = candidate.plan
            val slots = candidate.sessions
            plan.isActive && slots.isNotEmpty() && WorkoutSchedule.validationError(plan, slots) == null &&
                slots.all { it.planId == plan.id && WorkoutSchedule.weekday(it.day) != null } &&
                slots.map { it.cycleWeek to WorkoutSchedule.weekday(it.day) }.distinct().size == slots.size &&
                (plan.cycleWeeks == 1 || plan.cycleAnchorMonday != null)
        }
        return TrainingCalendarMonth(month, (1..month.lengthOfMonth()).map { n ->
            val date = month.atDay(n)
            val actual = logged[date.toString()].orEmpty().sortedWith(
                compareBy<TrainingSession> { it.completedAt ?: it.startedAt ?: Long.MIN_VALUE }.thenBy { it.id })
            // Do not back-project mutable current plans. Future slots are projections, not promises.
            val current = snapshot?.takeIf { date >= today }
            val slot = current?.let { WorkoutSchedule.sessionForDate(it.plan, it.sessions, date) }
            val kind = when {
                current == null -> CalendarScheduleKind.UNKNOWN
                slot == null || !WorkoutSchedule.isTraining(slot) -> CalendarScheduleKind.REST
                else -> CalendarScheduleKind.TRAINING
            }
            TrainingCalendarDay(date, actual, kind, slot?.label, slot?.id, current?.plan?.name)
        })
    }
}
