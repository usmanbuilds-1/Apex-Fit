package com.apexfit.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.utils.*
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RescheduledWorkoutsTest {
    @Test fun crossMonthAssociationIsUniqueReversibleAndCascadesOnWorkoutDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val plan = dao.insertWorkoutPlan(WorkoutPlan(name = "Upper", goal = "Strength", isActive = true))
            val slot = dao.insertPlanSession(PlanSession(planId = plan, label = "Upper", day = "Monday", focus = "Upper"))
            val start = LocalDate.parse("2026-11-29")
            val original = start.plusDays(1)
            val today = LocalDate.parse("2026-12-09")
            ScheduleLedger.capture(db, start, 100)
            val revision = requireNotNull(dao.getLatestScheduleRevision())
            val originalId = requireNotNull(ScheduleLedger.resolve(original, listOf(revision)).occurrenceId)
            val nextDate = original.plusWeeks(1)
            val nextId = requireNotNull(ScheduleLedger.resolve(nextDate, listOf(revision)).occurrenceId)
            val actual = TrainingSession("actual", "2026-12-02", "Upper", true, 30, 3,
                planSessionId = slot, startedAt = 200, completedAt = 300)
            dao.insertTrainingSession(actual)
            dao.insertTrainingSession(actual.copy(id = "already-bound", date = nextDate.toString(),
                scheduleOccurrenceId = nextId))
            assertEquals(listOf("actual"), RescheduledWorkouts.candidates(db, original, originalId, today, 1000).map { it.id })
            RescheduledWorkouts.link(db, original, originalId, actual.id, today, 1000)
            assertEquals(actual, dao.getTrainingSessionById(actual.id))
            assertTrue(runCatching { RescheduledWorkouts.link(db, nextDate, nextId, actual.id, today, 1000) }.isFailure)
            dao.deleteTrainingSession("already-bound")
            assertTrue(runCatching { RescheduledWorkouts.link(db, nextDate, nextId, actual.id, today, 1000) }.isFailure)
            val associations = dao.observeScheduleWorkoutAssociations("2026-11-01", "2026-11-30").first()
            val nov = TrainingCalendar.build(YearMonth.of(2026, 11), today, emptyList(), emptyList(),
                nowMs = 1000, revisions = listOf(revision), associations = associations)
            assertEquals(1, nov.recordedCompletedOccurrences)
            assertEquals(0, nov.completedSessions)
            assertEquals(actual.id, nov.days.last().rescheduledWorkout?.id)
            val dec = TrainingCalendar.build(YearMonth.of(2026, 12), today, listOf(actual), emptyList(),
                nowMs = 1000, revisions = listOf(revision),
                associations = dao.observeScheduleWorkoutAssociations("2026-12-01", "2026-12-31").first())
            assertEquals(0, dec.recordedCompletedOccurrences)
            assertEquals(1, dec.completedSessions)
            RescheduledWorkouts.unlink(db, original, originalId, today)
            assertNull(dao.getAssociationForWorkout(actual.id))
            RescheduledWorkouts.link(db, nextDate, nextId, actual.id, today, 1000)
            dao.deleteTrainingSession(actual.id)
            assertNull(dao.getScheduleWorkoutAssociation(nextId))
        } finally { db.close() }
    }

    @Test fun invalidCandidateNeverGetsCredit() {
        val actual = TrainingSession("actual", "2026-12-02", "Upper", true, 30, 3,
            planSessionId = 4, startedAt = 200, completedAt = 300)
        val date = LocalDate.parse("2026-11-30")
        val today = LocalDate.parse("2026-12-09")
        fun eligible(s: TrainingSession) = RescheduledWorkouts.eligible(s, date, 4, 100, today, 1000)
        assertTrue(eligible(actual))
        assertFalse(eligible(actual.copy(planSessionId = 5)))
        assertFalse(eligible(actual.copy(scheduleOccurrenceId = "1:2026-12-02:4")))
        assertFalse(eligible(actual.copy(startedAt = 99)))
        assertFalse(eligible(actual.copy(completedAt = 199)))
        assertFalse(eligible(actual.copy(date = date.toString())))
        assertFalse(eligible(actual.copy(date = "2026-12-10")))
        assertFalse(eligible(actual.copy(status = "partial")))
    }
}
