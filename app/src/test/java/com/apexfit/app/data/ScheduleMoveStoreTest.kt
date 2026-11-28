package com.apexfit.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.utils.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleMoveStoreTest {
    @Test fun reservationsAreIdempotentExclusiveCrossMonthAndReversible() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val plan = dao.insertWorkoutPlan(WorkoutPlan(name = "Upper/Lower", goal = "Strength", isActive = true))
            dao.insertPlanSession(PlanSession(planId = plan, label = "Upper", day = "Monday", focus = "Upper"))
            dao.insertPlanSession(PlanSession(planId = plan, label = "Lower", day = "Wednesday", focus = "Lower"))
            val today = LocalDate.parse("2026-11-29")
            val original = today.plusDays(1)
            val target = original.plusDays(3)
            ScheduleLedger.capture(db, today, 100)
            val revision = requireNotNull(dao.getLatestScheduleRevision())
            val id = requireNotNull(ScheduleLedger.resolve(original, listOf(revision)).occurrenceId)
            val next = original.plusDays(2)
            val nextId = requireNotNull(ScheduleLedger.resolve(next, listOf(revision)).occurrenceId)
            val row = ScheduleMoveStore.reserve(db, original, target, id, today, 200)
            assertEquals(row, ScheduleMoveStore.reserve(db, original, target, id, today, 201))
            assertEquals(row, dao.getScheduleMoveForTarget(target.toString()))
            assertEquals(listOf(row), dao.observeScheduleMoveReservations("2026-11-01", "2026-11-30").first())
            assertEquals(listOf(row), dao.observeScheduleMoveReservations("2026-12-01", "2026-12-31").first())
            assertTrue(runCatching { ScheduleMoveStore.reserve(db, next, target, nextId, today, 201) }.isFailure)
            assertTrue(runCatching { ScheduleMoveStore.reserve(db, original, target.plusDays(1), id, today, 201) }.isFailure)
            assertTrue(runCatching { ScheduleMoveStore.reserve(db, original, target, "999:$original:1", today, 201) }.isFailure)
            assertEquals(ReservationState.UPCOMING, ScheduleMoveStore.state(row, listOf(revision), today, 201))
            assertEquals(ReservationState.DUE, ScheduleMoveStore.state(row, listOf(revision), target, 201))
            assertEquals(ReservationState.ELAPSED, ScheduleMoveStore.state(row, listOf(revision), target.plusDays(1), 201))
            assertTrue(runCatching { ScheduleMoveStore.cancel(db, id, 199) }.isFailure)
            assertTrue(ScheduleMoveStore.cancel(db, id, 200))
            assertFalse(ScheduleMoveStore.cancel(db, id, 200))
            assertNull(dao.getScheduleMoveForTarget(target.toString()))
            assertEquals(nextId, ScheduleMoveStore.reserve(db, next, target, nextId, today, 202).occurrenceId)
        } finally { db.close() }
    }

    @Test fun staleReservationIsNotSilentlyRebound() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val today = LocalDate.parse("2026-11-29")
            val original = today.plusDays(1)
            val target = original.plusDays(1)
            val plan = dao.insertWorkoutPlan(WorkoutPlan(name = "Upper", goal = "Strength", isActive = true))
            dao.insertPlanSession(PlanSession(planId = plan, label = "Upper", day = "Monday", focus = "Upper"))
            ScheduleLedger.capture(db, today, 100)
            val first = requireNotNull(dao.getLatestScheduleRevision())
            val id = requireNotNull(ScheduleLedger.resolve(original, listOf(first)).occurrenceId)
            val row = ScheduleMoveStore.reserve(db, original, target, id, today, 200)
            dao.insertPlanSession(PlanSession(planId = plan, label = "Lower", day = "Tuesday", focus = "Lower"))
            ScheduleLedger.capture(db, today, 300)
            val second = requireNotNull(dao.getLatestScheduleRevision())
            assertEquals(ReservationState.STALE, ScheduleMoveStore.state(row, listOf(first, second), today, 301))
            assertEquals(row, dao.getScheduleMoveReservation(id))
            assertTrue(runCatching { ScheduleMoveStore.reserve(db, original, target, id, today, 301) }.isFailure)
            assertTrue(ScheduleMoveStore.cancel(db, id, 200))
            assertNull(dao.getScheduleMoveReservation(id))
        } finally { db.close() }
    }
}
