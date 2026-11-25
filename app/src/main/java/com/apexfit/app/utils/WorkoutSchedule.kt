package com.apexfit.app.utils

import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.WorkoutPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Calendar-based cycles, not completion-based. Missing slots are rest days.
 * Legacy weekly plans always use their original weekday slots.
 * An anchor is the Monday of cycle week 1 in the user's local calendar.
 */
object WorkoutSchedule {
    const val MAX_CYCLE_WEEKS = 12

    fun monday(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun cycleWeek(plan: WorkoutPlan, date: LocalDate): Int {
        val length = plan.cycleWeeks.coerceIn(1, MAX_CYCLE_WEEKS)
        if (length == 1) return 1
        val anchor = plan.cycleAnchorMonday?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return 1
        val offset = ChronoUnit.WEEKS.between(monday(anchor), monday(date))
        return Math.floorMod(offset, length.toLong()).toInt() + 1
    }

    fun weekday(day: String): DayOfWeek? =
        DayOfWeek.values().firstOrNull { it.name.equals(day.trim(), true) }

    fun weekSessions(plan: WorkoutPlan, sessions: List<PlanSession>, date: LocalDate): List<PlanSession> {
        val week = cycleWeek(plan, date)
        return sessions.filter { it.cycleWeek == week }
    }

    fun sessionForDate(plan: WorkoutPlan, sessions: List<PlanSession>, date: LocalDate): PlanSession? =
        weekSessions(plan, sessions, date).firstOrNull { weekday(it.day) == date.dayOfWeek }

    fun nextSession(plan: WorkoutPlan, sessions: List<PlanSession>, date: LocalDate): Pair<LocalDate, PlanSession>? =
        (0 until plan.cycleWeeks.coerceIn(1, MAX_CYCLE_WEEKS) * 7).firstNotNullOfOrNull { offset ->
            val day = date.plusDays(offset.toLong())
            sessionForDate(plan, sessions, day)?.let { day to it }
        }

    fun isTraining(session: PlanSession): Boolean =
        !session.label.contains("Rest", ignoreCase = true) && session.focus != "Muscle Recovery & Rest"

    fun trainingFrequency(plan: WorkoutPlan, sessions: List<PlanSession>, date: LocalDate): Int =
        weekSessions(plan, sessions, date).filter(::isTraining).mapNotNull { weekday(it.day) }.distinct().size

    fun averageTrainingFrequency(plan: WorkoutPlan, sessions: List<PlanSession>): Int {
        val length = plan.cycleWeeks.coerceIn(1, MAX_CYCLE_WEEKS)
        val count = sessions.filter { isTraining(it) && it.cycleWeek in 1..length }
            .mapNotNull { session -> weekday(session.day)?.let { session.cycleWeek to it } }.distinct().size
        return kotlin.math.round(count.toDouble() / length).toInt().coerceIn(0, 7)
    }

    fun validationError(plan: WorkoutPlan, sessions: List<PlanSession>): String? {
        if (plan.cycleWeeks !in 1..MAX_CYCLE_WEEKS) return "Invalid cycle length."
        if (sessions.any { it.cycleWeek !in 1..plan.cycleWeeks || (plan.cycleWeeks > 1 && weekday(it.day) == null) }) return "Invalid training day or cycle week."
        if (plan.cycleWeeks > 1 && sessions.map { it.cycleWeek to weekday(it.day) }.distinct().size != sessions.size)
            return "Only one session can be scheduled per weekday in each cycle week."
        if (plan.cycleAnchorMonday != null &&
            runCatching { LocalDate.parse(plan.cycleAnchorMonday).dayOfWeek == DayOfWeek.MONDAY }.getOrDefault(false).not())
            return "Cycle anchor must be a Monday."
        return null
    }
}
