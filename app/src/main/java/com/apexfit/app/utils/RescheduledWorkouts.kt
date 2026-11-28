package com.apexfit.app.utils

import androidx.room.withTransaction
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.RescheduledWorkoutSnapshot
import com.apexfit.app.data.ScheduleRevisionSnapshot
import com.apexfit.app.data.ScheduleWorkoutAssociation
import com.apexfit.app.data.TrainingSession
import java.time.LocalDate

/** Explicit retrospective credit for the original slot. Does not move the workout's actual date. */
object RescheduledWorkouts {
    fun coherent(session: TrainingSession, nowMs: Long): Boolean =
        session.startedAt != null && session.completedAt != null &&
            session.startedAt > 0 && session.completedAt >= session.startedAt &&
            session.completedAt <= nowMs &&
            ((session.completed && session.status == "completed") ||
                (!session.completed && session.status == "partial"))

    fun eligible(session: TrainingSession, scheduledDate: LocalDate, slotId: Long,
                 revisionRecordedAt: Long, today: LocalDate, nowMs: Long): Boolean {
        val actual = runCatching { LocalDate.parse(session.date) }.getOrNull() ?: return false
        return scheduledDate < today && actual != scheduledDate && actual <= today &&
            session.id.isNotBlank() && session.planSessionId == slotId && slotId > 0 &&
            session.scheduleOccurrenceId == null && coherent(session, nowMs) &&
            (session.startedAt ?: 0) >= revisionRecordedAt
    }

    fun verified(snapshot: RescheduledWorkoutSnapshot, occurrenceId: String, date: LocalDate,
                 slotId: Long, revisionRecordedAt: Long, today: LocalDate, nowMs: Long): TrainingSession? {
        val a = snapshot.association
        val session = snapshot.session ?: return null
        return session.takeIf { a.occurrenceId == occurrenceId && a.scheduledDate == date.toString() &&
            a.sessionId == session.id && a.recordedAt in 1..nowMs &&
            (session.completedAt ?: Long.MAX_VALUE) <= a.recordedAt &&
            ScheduleLedger.validOccurrence(occurrenceId, date.toString(), slotId) &&
            eligible(session, date, slotId, revisionRecordedAt, today, nowMs) }
    }

    private fun target(date: LocalDate, occurrenceId: String, revision: ScheduleRevisionSnapshot?,
                       today: LocalDate): Pair<ScheduleLedger.Day, Long> {
        require(date < today) { "Only elapsed scheduled occurrences can be linked" }
        val resolved = ScheduleLedger.resolve(date, listOfNotNull(revision))
        require(resolved.kind == CalendarScheduleKind.TRAINING && resolved.occurrenceId == occurrenceId) {
            "Schedule occurrence no longer matches"
        }
        return resolved to requireNotNull(revision).revision.recordedAt
    }

    private fun automaticEvidence(sessions: List<TrainingSession>, slotId: Long, nowMs: Long): Boolean =
        sessions.any { it.planSessionId == slotId && coherent(it, nowMs) }

    suspend fun candidates(db: AppDatabase, date: LocalDate, occurrenceId: String,
                           today: LocalDate = LocalDate.now(), nowMs: Long = System.currentTimeMillis()):
        List<TrainingSession> = db.withTransaction {
        val dao = db.fitnessDao()
        val (day, recordedAt) = target(date, occurrenceId, dao.getScheduleRevisionForDate(date.toString()), today)
        val slotId = requireNotNull(day.slotId)
        if (dao.getScheduleWorkoutAssociation(occurrenceId) != null ||
            automaticEvidence(dao.getOccurrenceWorkouts(date.toString(), occurrenceId), slotId, nowMs))
            return@withTransaction emptyList()
        dao.getReschedulingCandidates(slotId, date.toString(), today.toString(), recordedAt, nowMs)
            .filter { eligible(it, date, slotId, recordedAt, today, nowMs) }
    }

    suspend fun link(db: AppDatabase, date: LocalDate, occurrenceId: String, sessionId: String,
                     today: LocalDate = LocalDate.now(), nowMs: Long = System.currentTimeMillis()): Unit =
        db.withTransaction {
            val dao = db.fitnessDao()
            val (day, recordedAt) = target(date, occurrenceId, dao.getScheduleRevisionForDate(date.toString()), today)
            val slotId = requireNotNull(day.slotId)
            val session = requireNotNull(dao.getTrainingSessionById(sessionId)) { "Workout no longer exists" }
            require(eligible(session, date, slotId, recordedAt, today, nowMs)) { "Workout cannot satisfy this occurrence" }
            require(!automaticEvidence(dao.getOccurrenceWorkouts(date.toString(), occurrenceId), slotId, nowMs)) {
                "A verified workout already satisfies this occurrence"
            }
            val current = dao.getScheduleWorkoutAssociation(occurrenceId)
            if (current?.sessionId == sessionId) return@withTransaction
            require(current == null && dao.getAssociationForWorkout(sessionId) == null) {
                "Clear the existing association before linking another workout"
            }
            dao.insertScheduleWorkoutAssociation(ScheduleWorkoutAssociation(occurrenceId, date.toString(), sessionId, nowMs))
        }

    suspend fun unlink(db: AppDatabase, date: LocalDate, occurrenceId: String,
                       today: LocalDate = LocalDate.now()): Unit = db.withTransaction {
        val dao = db.fitnessDao()
        target(date, occurrenceId, dao.getScheduleRevisionForDate(date.toString()), today)
        dao.clearScheduleWorkoutAssociation(occurrenceId)
    }
}
