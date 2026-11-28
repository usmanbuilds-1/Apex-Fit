package com.apexfit.app.utils

import com.apexfit.app.data.*
import com.apexfit.app.data.TrainingSession
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** Current seven-day projection only, never an immutable record of previously displayed targets. */
data class WeeklyNutritionProjection(val monday: LocalDate, val trainingDates: Set<LocalDate>) {
    init {
        require(monday.dayOfWeek.value == 1 && trainingDates.all { it in monday..monday.plusDays(6) })
    }
    val frequency: Int get() = trainingDates.size
}

object WeeklyNutritionSchedule {
    fun project(today: LocalDate, plans: List<CalendarPlanSnapshot>,
                revisions: List<ScheduleRevisionSnapshot>, reservations: List<ScheduleMoveReservation>,
                sessions: List<TrainingSession>, associations: List<RescheduledWorkoutSnapshot>,
                fallbackFrequency: Int, nowMs: Long = System.currentTimeMillis()): WeeklyNutritionProjection? {
        val monday = WorkoutSchedule.monday(today)
        val dates = (0L..6L).map(monday::plusDays)
        // No reliable active schedule: do not invent particular weekdays from a frequency preference.
        val active = plans.singleOrNull()?.takeIf { p ->
            p.plan.isActive && p.sessions.isNotEmpty() &&
                WorkoutSchedule.validationError(p.plan, p.sessions) == null &&
                p.sessions.all { it.planId == p.plan.id && WorkoutSchedule.weekday(it.day) != null } &&
                p.sessions.map { it.cycleWeek to WorkoutSchedule.weekday(it.day) }.distinct().size == p.sessions.size &&
                (p.plan.cycleWeeks == 1 || p.plan.cycleAnchorMonday != null)
        } ?: return null
        val actual = sessions.filter { it.completed && it.status == "completed" &&
            (it.completedAt ?: it.startedAt ?: 0L) <= nowMs }.map { it.date }.toSet()
        val validAssociations = associations.mapNotNull { entry ->
            val date = runCatching { LocalDate.parse(entry.association.scheduledDate) }.getOrNull()
                ?: return@mapNotNull null
            val day = ScheduleLedger.resolve(date, revisions)
            val rev = revisions.singleOrNull { it.revision.id == day.revisionId } ?: return@mapNotNull null
            val id = day.occurrenceId ?: return@mapNotNull null
            val slot = day.slotId ?: return@mapNotNull null
            RescheduledWorkouts.verified(entry, id, date, slot, rev.revision.recordedAt, today, nowMs)
                ?.let { date to it.date }
        }
        val movedSources = validAssociations.map { it.first }.toSet()
        val movedTargets = validAssociations.map { it.second }.toSet()
        val training = dates.filter { day ->
            val choice = TodayWorkoutResolver.resolve(day, listOf(active), revisions, reservations,
                sessions, associations, nowMs)
            // Actual completion always wins. Consumed reservations remain represented by
            // verified cross-date associations. No saved log alone is not proof of a missed day.
            day.toString() in actual || day.toString() in movedTargets ||
                (day !in movedSources && choice.session != null)
        }.toSet()
        return WeeklyNutritionProjection(monday, training)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(dao: FitnessDao, today: LocalDate, fallbackFrequency: Int): Flow<WeeklyNutritionProjection?> {
        val monday = WorkoutSchedule.monday(today)
        val sunday = monday.plusDays(6)
        return dao.observeScheduleMoveReservations(monday.toString(), sunday.toString()).flatMapLatest { rows ->
            val endpoints = rows.mapNotNull(ScheduleMoveStore::decode).flatMap { listOf(it.originalDate, it.targetDate) }
            val start = minOf(monday, endpoints.minOrNull() ?: monday).toString()
            val end = maxOf(sunday, endpoints.maxOrNull() ?: sunday).toString()
            // Association query includes either endpoint, including consumed reservations across week boundaries.
            combine(dao.observeCalendarPlans(), dao.observeScheduleRevisions(start, end),
                dao.observeCalendarSessions(start, end), dao.observeScheduleAssociationsForNutritionWeek(monday.toString(), sunday.toString())) {
                    plans, revisions, sessions, associations ->
                Triple(plans, sessions, associations) to revisions
            }.flatMapLatest { (facts, revisions) ->
                val (plans, sessions, associations) = facts
                val dates = associations.mapNotNull { runCatching { LocalDate.parse(it.association.scheduledDate) }.getOrNull() }
                val earliest = minOf(LocalDate.parse(start), dates.minOrNull() ?: monday).toString()
                if (earliest == start) flowOf(project(today, plans, revisions, rows, sessions, associations, fallbackFrequency))
                else dao.observeScheduleRevisions(earliest, end).map { expanded ->
                    project(today, plans, expanded, rows, sessions, associations, fallbackFrequency)
                }
            }
        }
    }
}
