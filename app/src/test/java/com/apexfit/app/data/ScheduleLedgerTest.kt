package com.apexfit.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.utils.CalendarScheduleKind
import com.apexfit.app.utils.ScheduleLedger
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
class ScheduleLedgerTest {
    @Test fun revisionsPreserveRotationAndSameDayEditsDoNotRewritePriorDays() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val monday = LocalDate.parse("2026-11-23")
            val plan = dao.insertWorkoutPlan(WorkoutPlan(name = "A/B", goal = "Strength", isActive = true,
                cycleWeeks = 2, cycleAnchorMonday = monday.toString()))
            val a = dao.insertPlanSession(PlanSession(planId = plan, label = "A", day = "Tuesday", focus = "Upper", cycleWeek = 1))
            dao.insertPlanSession(PlanSession(planId = plan, label = "B", day = "Tuesday", focus = "Lower", cycleWeek = 2))
            ScheduleLedger.capture(db, monday, 100)
            val first = requireNotNull(dao.getLatestScheduleRevision())
            ScheduleLedger.capture(db, monday.plusDays(1), 200)
            assertEquals(first.revision.id, dao.getLatestScheduleRevision()?.revision?.id)
            val before = dao.observeScheduleRevisions(monday.toString(), monday.plusWeeks(2).toString()).first()
            assertEquals(CalendarScheduleKind.UNKNOWN, ScheduleLedger.resolve(monday, before).kind)
            val dayA = ScheduleLedger.resolve(monday.plusDays(1), before)
            assertEquals("A", dayA.label)
            assertEquals(a, requireNotNull(dayA.slotId))
            assertEquals("B", ScheduleLedger.resolve(monday.plusDays(8), before).label)
            assertEquals(CalendarScheduleKind.REST, ScheduleLedger.resolve(monday.plusDays(2), before).kind)
            db.withTransaction {
                dao.deleteSessionsForPlan(plan)
                dao.insertPlanSession(PlanSession(planId = plan, label = "New", day = "Thursday", focus = "Back", cycleWeek = 1))
                ScheduleLedger.capture(db, monday.plusDays(1), 300)
            }
            db.withTransaction {
                dao.deleteSessionsForPlan(plan)
                dao.insertPlanSession(PlanSession(planId = plan, label = "Final", day = "Thursday", focus = "Back", cycleWeek = 1))
                ScheduleLedger.capture(db, monday.plusDays(1), 400)
            }
            val revisions = dao.observeScheduleRevisions(monday.toString(), monday.plusWeeks(2).toString()).first()
            assertEquals(3, revisions.size)
            assertEquals(dayA, ScheduleLedger.resolve(monday.plusDays(1), revisions))
            assertEquals("Final", ScheduleLedger.resolve(monday.plusDays(3), revisions).label)
            assertEquals(CalendarScheduleKind.REST, ScheduleLedger.resolve(monday.plusDays(8), revisions).kind)
            dao.deleteAllPlans()
            assertEquals(dayA, ScheduleLedger.resolve(monday.plusDays(1),
                dao.observeScheduleRevisions(monday.toString(), monday.plusWeeks(2).toString()).first()))
            ScheduleLedger.capture(db, monday.plusDays(3), 500)
            assertEquals(CalendarScheduleKind.UNKNOWN, ScheduleLedger.resolve(monday.plusDays(4),
                dao.observeScheduleRevisions(monday.toString(), monday.plusWeeks(2).toString()).first()).kind)
        } finally { db.close() }
    }

    @Test fun upgradeBaselineIsForwardOnlyIdempotentAndDoesNotSeedEmptyInstall() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val today = LocalDate.parse("2026-11-23")
            ScheduleLedger.ensureBaseline(db, today, 100)
            assertNull(dao.getLatestScheduleRevision())
            val id = dao.insertWorkoutPlan(WorkoutPlan(name = "Existing", goal = "Strength", isActive = true))
            dao.insertPlanSession(PlanSession(planId = id, label = "A", day = "Tuesday", focus = "Upper"))
            ScheduleLedger.ensureBaseline(db, today, 200)
            val saved = requireNotNull(dao.getLatestScheduleRevision())
            ScheduleLedger.ensureBaseline(db, today.plusDays(3), 300)
            assertEquals(saved.revision.id, dao.getLatestScheduleRevision()?.revision?.id)
            assertEquals(CalendarScheduleKind.UNKNOWN, ScheduleLedger.resolve(today, listOf(saved)).kind)
            assertEquals("A", ScheduleLedger.resolve(today.plusDays(1), listOf(saved)).label)
        } finally { db.close() }
    }

    @Test fun transactionRollbackDoesNotPublishRevision() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            try {
                db.withTransaction {
                    val id = dao.insertWorkoutPlan(WorkoutPlan(name = "Rollback", goal = "Strength", isActive = true))
                    dao.insertPlanSession(PlanSession(planId = id, label = "A", day = "Tuesday", focus = "Upper"))
                    ScheduleLedger.capture(db, LocalDate.parse("2026-11-23"), 100)
                    throw IllegalStateException("abort")
                }
            } catch (_: IllegalStateException) { }
            assertNull(dao.getLatestScheduleRevision())
            assertTrue(dao.getAllPlans().isEmpty())
        } finally { db.close() }
    }
}
