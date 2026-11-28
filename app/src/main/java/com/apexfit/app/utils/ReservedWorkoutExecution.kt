package com.apexfit.app.utils

import androidx.room.withTransaction
import com.apexfit.app.data.ActiveSession
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.ScheduleMoveReservation
import com.apexfit.app.data.ScheduleWorkoutAssociation
import com.apexfit.app.data.TrainingSession
import java.time.LocalDate

/** Reservation credit is an explicit start action, never inferred from a matching workout label. */
object ReservedWorkoutExecution {
    fun validDraft(draft: ActiveSession): Boolean {
        val id = draft.reservedOccurrenceId
        val timestamp = draft.reservationRecordedAt
        if (id == null) return timestamp == null
        if (timestamp == null || timestamp <= 0 || timestamp > draft.startTime ||
            draft.scheduleOccurrenceId != null) return false
        val original = id.split(':').getOrNull(1)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: return false
        val actual = draft.startedLocalDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        return original < actual && ScheduleLedger.validOccurrence(id, original.toString(), draft.planSessionId.toLongOrNull())
    }

    suspend fun validateStart(db: AppDatabase, expected: ScheduleMoveReservation, chosen: PlanSession,
                              today: LocalDate, nowMs: Long): ScheduleMoveReservation = db.withTransaction {
        val dao = db.fitnessDao()
        val row = requireNotNull(dao.getScheduleMoveReservation(expected.occurrenceId)) { "Reservation removed" }
        require(row == expected) { "Reservation changed" }
        val move = requireNotNull(ScheduleMoveStore.decode(row))
        require(move.targetDate == today && row.recordedAt <= nowMs && chosen.id == row.slotId)
        val source = dao.getScheduleRevisionForDate(row.originalDate)
        val destination = dao.getScheduleRevisionForDate(row.targetDate)
        val revisions = listOfNotNull(source, destination).distinctBy { it.revision.id }
        require(ScheduleMoveStore.state(row, revisions, today, nowMs) == ReservationState.DUE)
        val active = dao.getAllPlans().filter { it.isActive }.singleOrNull()
        require(active != null && active.id == chosen.planId && source?.revision?.planId == active.id)
        require(dao.getSessionsForPlan(active.id).singleOrNull { it.id == chosen.id } == chosen &&
            WorkoutSchedule.isTraining(chosen) && ScheduleLedger.resolve(move.originalDate, revisions).label == chosen.label)
        require(dao.getScheduleWorkoutAssociation(row.occurrenceId) == null &&
            dao.getOccurrenceWorkouts(row.originalDate, row.occurrenceId).none { RescheduledWorkouts.coherent(it, nowMs) }) {
            "Original occurrence already has workout evidence"
        }
        row
    }

    /** Called inside the same outer Room transaction as workout + sets + PRs.
     * A cancelled/changed/conflicting reservation never prevents saving real work.
     * On success consume reservation only after inserting a unique association.
     */
    suspend fun attributeSavedWorkout(db: AppDatabase, draft: ActiveSession, workout: TrainingSession,
                                      nowMs: Long): Boolean = db.withTransaction {
        val id = draft.reservedOccurrenceId ?: return@withTransaction false
        if (!validDraft(draft)) return@withTransaction false
        val dao = db.fitnessDao()
        val row = dao.getScheduleMoveReservation(id) ?: return@withTransaction false
        val move = ScheduleMoveStore.decode(row) ?: return@withTransaction false
        if (row.recordedAt != draft.reservationRecordedAt || row.targetDate != draft.startedLocalDate ||
            row.slotId != workout.planSessionId || workout.id != draft.sessionId ||
            workout.startedAt != draft.startTime || workout.date != row.targetDate ||
            workout.scheduleOccurrenceId != null || !RescheduledWorkouts.coherent(workout, nowMs))
            return@withTransaction false
        val source = dao.getScheduleRevisionForDate(row.originalDate)
        val destination = dao.getScheduleRevisionForDate(row.targetDate)
        val revisions = listOfNotNull(source, destination).distinctBy { it.revision.id }
        if (!ProspectiveWorkoutMoves.stillMatches(move, revisions) ||
            (source?.revision?.recordedAt ?: Long.MAX_VALUE) > draft.startTime ||
            dao.getScheduleWorkoutAssociation(id) != null || dao.getAssociationForWorkout(workout.id) != null ||
            dao.getOccurrenceWorkouts(row.originalDate, id).any { RescheduledWorkouts.coherent(it, nowMs) })
            return@withTransaction false
        dao.insertScheduleWorkoutAssociation(ScheduleWorkoutAssociation(id, row.originalDate, workout.id, nowMs))
        check(dao.deleteScheduleMoveReservation(id) == 1)
        true
    }
}
