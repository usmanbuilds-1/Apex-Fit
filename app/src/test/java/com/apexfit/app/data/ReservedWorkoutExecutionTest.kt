package com.apexfit.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.utils.*
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReservedWorkoutExecutionTest {
    @Test fun explicitStartAndSavePreserveActualDateAndConsumeReservationOnce() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val plan = dao.insertWorkoutPlan(WorkoutPlan(name = "Upper", goal = "Strength", isActive = true))
            val slotId = dao.insertPlanSession(PlanSession(planId = plan, label = "Upper", day = "Monday", focus = "Upper"))
            val slot = dao.getSessionsForPlan(plan).single()
            val today = LocalDate.parse("2026-11-29")
            val original = today.plusDays(1)
            val target = original.plusDays(1)
            ScheduleLedger.capture(db, today, 100)
            val revision = requireNotNull(dao.getLatestScheduleRevision())
            val occurrence = requireNotNull(ScheduleLedger.resolve(original, listOf(revision)).occurrenceId)
            val row = ScheduleMoveStore.reserve(db, original, target, occurrence, today, 200)
            assertTrue(runCatching { ReservedWorkoutExecution.validateStart(db, row, slot, original, 300) }.isFailure)
            assertEquals(row, ReservedWorkoutExecution.validateStart(db, row, slot, target, 300))
            val draft = ActiveSession(slotId.toString(), "Upper", startTime = 300, exercises = mutableListOf(),
                startedLocalDate = target.toString(), reservedOccurrenceId = occurrence, reservationRecordedAt = 200)
            assertTrue(ReservedWorkoutExecution.validDraft(draft))
            assertFalse(ReservedWorkoutExecution.validDraft(draft.copy(scheduleOccurrenceId = occurrence)))
            val saved = TrainingSession(draft.sessionId, target.toString(), "Upper", true, 30, 3,
                planSessionId = slotId, startedAt = 300, completedAt = 400)
            dao.insertTrainingSession(saved)
            assertTrue(ReservedWorkoutExecution.attributeSavedWorkout(db, draft, saved, 400))
            assertNull(dao.getScheduleMoveReservation(occurrence))
            assertEquals(saved, dao.getTrainingSessionById(saved.id))
            assertEquals(saved.id, dao.getScheduleWorkoutAssociation(occurrence)?.sessionId)
            assertFalse(ReservedWorkoutExecution.attributeSavedWorkout(db, draft, saved, 500))
        } finally { db.close() }
    }

    @Test fun cancelledReservationDoesNotPreventSavingActualWorkout() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val draft = ActiveSession("12", "Upper", startTime = 300, exercises = mutableListOf(),
                startedLocalDate = "2026-12-01", reservedOccurrenceId = "1:2026-11-30:12", reservationRecordedAt = 200)
            val saved = TrainingSession(draft.sessionId, "2026-12-01", "Upper", true, 30, 3,
                planSessionId = 12, startedAt = 300, completedAt = 400)
            dao.insertTrainingSession(saved)
            assertFalse(ReservedWorkoutExecution.attributeSavedWorkout(db, draft, saved, 400))
            assertEquals(saved, dao.getTrainingSessionById(saved.id))
            assertNull(dao.getAssociationForWorkout(saved.id))
        } finally { db.close() }
    }
}
