package com.apexfit.app.utils

import com.apexfit.app.data.CalendarPlanSnapshot
import com.apexfit.app.data.RescheduledWorkoutSnapshot
import com.apexfit.app.data.ScheduleDisposition
import com.apexfit.app.data.ScheduleRevisionSnapshot
import com.apexfit.app.data.TrainingSession
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarScheduleKind { TRAINING, REST, UNKNOWN }
enum class CalendarLogKind { COMPLETED, PARTIAL, NONE }
enum class CalendarScheduleSource { RECORDED, LIVE_PROJECTION, UNKNOWN }
enum class RecordedOccurrenceStatus { NOT_APPLICABLE, COMPLETED, PARTIAL, MISSED, EXCUSED, UNRESOLVED }
enum class OccurrenceDecision { MISSED, EXCUSED }

data class TrainingCalendarDay(
    val date: LocalDate,
    val sessions: List<TrainingSession>,
    val scheduleKind: CalendarScheduleKind,
    val plannedLabel: String?,
    val plannedSessionId: Long?,
    val planName: String?,
    val scheduleSource: CalendarScheduleSource = CalendarScheduleSource.UNKNOWN,
    val recordedOccurrenceId: String? = null,
    val explicitDecision: OccurrenceDecision? = null,
    val rescheduledWorkout: TrainingSession? = null,
    val hasManualAssociation: Boolean = false
) {
    val completedCount: Int get() = sessions.count { it.completed }
    val partialCount: Int get() = sessions.count { !it.completed && it.status == "partial" }
    /** Count an occurrence once, independently of the number of saved workouts.
     * Missing evidence is unresolved, not a confirmed miss. No fractional credit.
     */
    val occurrenceStatus: RecordedOccurrenceStatus get() {
        val occurrence = recordedOccurrenceId ?: return RecordedOccurrenceStatus.NOT_APPLICABLE
        if (scheduleSource != CalendarScheduleSource.RECORDED || scheduleKind != CalendarScheduleKind.TRAINING ||
            !ScheduleLedger.validOccurrence(occurrence, date.toString(), plannedSessionId))
            return RecordedOccurrenceStatus.NOT_APPLICABLE
        val linked = sessions.filter { it.date == date.toString() && it.scheduleOccurrenceId == occurrence &&
            it.planSessionId == plannedSessionId && it.startedAt != null && it.completedAt != null &&
            it.completedAt >= it.startedAt }
        val evidence = linked + listOfNotNull(rescheduledWorkout)
        return when {
            evidence.any { it.completed && it.status == "completed" } -> RecordedOccurrenceStatus.COMPLETED
            evidence.any { !it.completed && it.status == "partial" } -> RecordedOccurrenceStatus.PARTIAL
            explicitDecision == OccurrenceDecision.MISSED -> RecordedOccurrenceStatus.MISSED
            explicitDecision == OccurrenceDecision.EXCUSED -> RecordedOccurrenceStatus.EXCUSED
            else -> RecordedOccurrenceStatus.UNRESOLVED
        }
    }
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
    val recordedCompletedOccurrences: Int get() = days.count { it.occurrenceStatus == RecordedOccurrenceStatus.COMPLETED }
    val recordedPartialOccurrences: Int get() = days.count { it.occurrenceStatus == RecordedOccurrenceStatus.PARTIAL }
    val unresolvedOccurrences: Int get() = days.count { it.occurrenceStatus == RecordedOccurrenceStatus.UNRESOLVED }
    val missedOccurrences: Int get() = days.count { it.occurrenceStatus == RecordedOccurrenceStatus.MISSED }
    val excusedOccurrences: Int get() = days.count { it.occurrenceStatus == RecordedOccurrenceStatus.EXCUSED }
    val recordedOccurrences: Int get() = recordedCompletedOccurrences + recordedPartialOccurrences +
        missedOccurrences + excusedOccurrences + unresolvedOccurrences
}

object TrainingCalendar {
    fun build(month: YearMonth, today: LocalDate, sessions: List<TrainingSession>,
              activePlans: List<CalendarPlanSnapshot>, nowMs: Long = System.currentTimeMillis(),
              revisions: List<ScheduleRevisionSnapshot> = emptyList(),
              dispositions: List<ScheduleDisposition> = emptyList(),
              associations: List<RescheduledWorkoutSnapshot> = emptyList()): TrainingCalendarMonth {
        val byOccurrence = associations.groupBy { it.association.occurrenceId }
        val duplicateSessions = associations.groupingBy { it.association.sessionId }.eachCount()
        val revisionsById = revisions.associateBy { it.revision.id }
        val decisions = dispositions.filter { it.recordedAt in 1..nowMs }.groupBy { it.occurrenceId }
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
            // Past schedules must come from the immutable ledger, never the live plan.
            // A saved workout on that day does not establish completion of this occurrence.
            if (date < today) {
                val recorded = ScheduleLedger.resolve(date, revisions)
                return@map TrainingCalendarDay(date, actual, recorded.kind, recorded.label,
                    recorded.slotId, recorded.planName,
                    if (recorded.kind == CalendarScheduleKind.UNKNOWN) CalendarScheduleSource.UNKNOWN
                    else CalendarScheduleSource.RECORDED, recorded.occurrenceId,
                    recorded.occurrenceId?.let { id ->
                        decisions[id]?.singleOrNull()?.takeIf { it.scheduledDate == date.toString() }
                            ?.decision?.let { runCatching { OccurrenceDecision.valueOf(it) }.getOrNull() }
                    },
                    recorded.occurrenceId?.let { id ->
                        val a = byOccurrence[id]?.singleOrNull()
                        val slotId = recorded.slotId
                        val recordedAt = revisionsById[recorded.revisionId]?.revision?.recordedAt
                        if (a == null || slotId == null || recordedAt == null ||
                            duplicateSessions[a.association.sessionId] != 1) null
                        else RescheduledWorkouts.verified(a, id, date, slotId, recordedAt, today, nowMs)
                    }, recorded.occurrenceId?.let { byOccurrence[it]?.isNotEmpty() == true } ?: false)
            }
            // Today follows live execution; future slots are projections, not promises.
            val current = snapshot?.takeIf { date >= today }
            val slot = current?.let { WorkoutSchedule.sessionForDate(it.plan, it.sessions, date) }
            val kind = when {
                current == null -> CalendarScheduleKind.UNKNOWN
                slot == null || !WorkoutSchedule.isTraining(slot) -> CalendarScheduleKind.REST
                else -> CalendarScheduleKind.TRAINING
            }
            TrainingCalendarDay(date, actual, kind, slot?.label, slot?.id, current?.plan?.name,
                if (current == null) CalendarScheduleSource.UNKNOWN else CalendarScheduleSource.LIVE_PROJECTION)
        })
    }
}
