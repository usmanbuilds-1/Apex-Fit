package com.apexfit.app.utils

import androidx.room.withTransaction
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.ScheduleMoveReservation
import com.apexfit.app.data.ScheduleRevisionSnapshot
import java.time.LocalDate

enum class ReservationState { UPCOMING, DUE, ELAPSED, STALE }

object ScheduleMoveStore {
    fun decode(row: ScheduleMoveReservation): ProspectiveWorkoutMove? = runCatching {
        val original = LocalDate.parse(row.originalDate)
        val target = LocalDate.parse(row.targetDate)
        require(original.toString() == row.originalDate && target.toString() == row.targetDate)
        require(target > original && row.revisionId > 0 && row.slotId > 0 && row.recordedAt > 0)
        require(row.occurrenceId == "${row.revisionId}:$original:${row.slotId}")
        ProspectiveWorkoutMove(row.occurrenceId, original, target, row.revisionId, row.slotId)
    }.getOrNull()

    fun state(row: ScheduleMoveReservation, revisions: List<ScheduleRevisionSnapshot>,
              today: LocalDate, nowMs: Long): ReservationState {
        val move = decode(row) ?: return ReservationState.STALE
        if (row.recordedAt > nowMs || !ProspectiveWorkoutMoves.stillMatches(move, revisions))
            return ReservationState.STALE
        return when {
            move.targetDate > today -> ReservationState.UPCOMING
            move.targetDate == today -> ReservationState.DUE
            else -> ReservationState.ELAPSED
        }
    }

    /** Re-read both effective revisions inside a single transaction; stale UI cannot reserve a new identity. */
    suspend fun reserve(db: AppDatabase, original: LocalDate, target: LocalDate, expectedOccurrenceId: String,
                        today: LocalDate = LocalDate.now(),
                        nowMs: Long = System.currentTimeMillis()): ScheduleMoveReservation = db.withTransaction {
        val dao = db.fitnessDao()
        require(nowMs > 0)
        val source = dao.getScheduleRevisionForDate(original.toString())
        val destination = dao.getScheduleRevisionForDate(target.toString())
        val revisions = listOfNotNull(source, destination).distinctBy { it.revision.id }
        require(revisions.all { it.revision.recordedAt <= nowMs }) { "Schedule timestamp is invalid" }
        val proposed = ProspectiveWorkoutMoves.propose(original, target, today, revisions)
        val move = (proposed as? MoveValidation.Allowed)?.move
            ?: throw IllegalArgumentException("Cannot reserve: ${(proposed as MoveValidation.Rejected).reason}")
        require(move.occurrenceId == expectedOccurrenceId) { "Schedule changed; refresh before reserving" }
        val conflicts = dao.getScheduleMoveConflicts(original.toString(), target.toString())
        val same = conflicts.singleOrNull()
        if (same != null && decode(same) == move && same.recordedAt <= nowMs) return@withTransaction same
        require(conflicts.isEmpty()) { "A reservation already uses this date; remove it explicitly" }
        require(dao.getScheduleWorkoutAssociation(move.occurrenceId) == null &&
            dao.getOccurrenceWorkouts(original.toString(), move.occurrenceId).isEmpty()) {
            "This occurrence already has workout evidence"
        }
        val row = ScheduleMoveReservation(move.occurrenceId, original.toString(), target.toString(),
            move.revisionId, move.slotId, nowMs)
        dao.insertScheduleMoveReservation(row)
        row
    }

    /** Cancels only a reservation. Never deletes a workout or its association. */
    suspend fun cancel(db: AppDatabase, occurrenceId: String, expectedRecordedAt: Long): Boolean =
        db.withTransaction {
            val dao = db.fitnessDao()
            val row = dao.getScheduleMoveReservation(occurrenceId) ?: return@withTransaction false
            require(row.recordedAt == expectedRecordedAt) { "Reservation changed; refresh before cancelling" }
            dao.deleteScheduleMoveReservation(occurrenceId) == 1
        }
}
