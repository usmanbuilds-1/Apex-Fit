package com.apexfit.app.utils

import androidx.room.withTransaction
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.ScheduleRevision
import com.apexfit.app.data.ScheduleRevisionSlot
import com.apexfit.app.data.ScheduleRevisionSnapshot
import java.time.LocalDate

/** Captures actual persisted state inside the caller's Room transaction.
 * Day-level adherence revisions take effect tomorrow, never retroactively.
 * Today's workout execution continues to use the live plan.
 */
object ScheduleLedger {
    suspend fun capture(db: AppDatabase, today: LocalDate = LocalDate.now(),
                        recordedAt: Long = System.currentTimeMillis()) = db.withTransaction {
        val dao = db.fitnessDao()
        val active = dao.getAllPlans().filter { it.isActive }.singleOrNull()
        val sessions = active?.let { dao.getSessionsForPlan(it.id) }.orEmpty()
        val plan = active?.takeIf { p ->
            p.id > 0 && sessions.isNotEmpty() &&
                WorkoutSchedule.validationError(p, sessions) == null &&
                (p.cycleWeeks == 1 || p.cycleAnchorMonday != null) &&
                sessions.all { it.id > 0 && it.planId == p.id && WorkoutSchedule.weekday(it.day) != null } &&
                sessions.map { it.cycleWeek to WorkoutSchedule.weekday(it.day) }.distinct().size == sessions.size
        }
        val previous = dao.getLatestScheduleRevision()
        val effective = maxOf(today.plusDays(1), previous?.revision?.effectiveFrom?.let(LocalDate::parse)
            ?: today.plusDays(1))
        val next = ScheduleRevision(effectiveFrom = effective.toString(), recordedAt = recordedAt,
            planId = plan?.id, planName = plan?.name, cycleWeeks = plan?.cycleWeeks,
            anchorMonday = plan?.cycleAnchorMonday)
        val slots = if (plan == null) emptyList() else sessions.map { s ->
            ScheduleRevisionSlot(0, s.id, s.cycleWeek, requireNotNull(WorkoutSchedule.weekday(s.day)).value,
                s.label, WorkoutSchedule.isTraining(s))
        }.sortedBy { it.slotId }
        if (previous != null && previous.revision.copy(id = 0, effectiveFrom = next.effectiveFrom,
                recordedAt = next.recordedAt) == next &&
            previous.slots.map { it.copy(revisionId = 0) }.sortedBy { it.slotId } == slots) return@withTransaction
        val id = dao.insertScheduleRevision(next)
        if (slots.isNotEmpty()) dao.insertScheduleRevisionSlots(slots.map { it.copy(revisionId = id) })
    }

    data class Day(val kind: CalendarScheduleKind, val revisionId: Long? = null,
                   val planName: String? = null, val slotId: Long? = null, val label: String? = null,
                   val occurrenceId: String? = null)

    /** Resolution uses only immutable snapshot values. Missing or malformed evidence is unknown. */
    fun resolve(date: LocalDate, revisions: List<ScheduleRevisionSnapshot>): Day {
        val selected = revisions.mapNotNull { s ->
            runCatching { LocalDate.parse(s.revision.effectiveFrom) }.getOrNull()?.let { it to s }
        }.filter { it.first <= date }.maxWithOrNull(compareBy<Pair<LocalDate, ScheduleRevisionSnapshot>> { it.first }
            .thenBy { it.second.revision.id })?.second ?: return Day(CalendarScheduleKind.UNKNOWN)
        val revision = selected.revision
        val length = revision.cycleWeeks ?: return Day(CalendarScheduleKind.UNKNOWN)
        if (revision.id <= 0 || revision.planId == null || length !in 1..WorkoutSchedule.MAX_CYCLE_WEEKS ||
            selected.slots.isEmpty() || selected.slots.any { it.revisionId != revision.id || it.slotId <= 0 ||
                it.weekday !in 1..7 || it.cycleWeek !in 1..length } ||
            selected.slots.map { it.cycleWeek to it.weekday }.distinct().size != selected.slots.size)
            return Day(CalendarScheduleKind.UNKNOWN)
        val anchor = revision.anchorMonday?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (length > 1 && (anchor == null || anchor.dayOfWeek.value != 1)) return Day(CalendarScheduleKind.UNKNOWN)
        val week = if (length == 1) 1 else Math.floorMod(
            java.time.temporal.ChronoUnit.WEEKS.between(requireNotNull(anchor), WorkoutSchedule.monday(date)),
            length.toLong()).toInt() + 1
        val slot = selected.slots.singleOrNull { it.cycleWeek == week && it.weekday == date.dayOfWeek.value }
        return if (slot == null || !slot.training) Day(CalendarScheduleKind.REST, revision.id, revision.planName,
            slot?.slotId, slot?.label)
        else Day(CalendarScheduleKind.TRAINING, revision.id, revision.planName, slot.slotId, slot.label,
            "${revision.id}:$date:${slot.slotId}")
    }
}
