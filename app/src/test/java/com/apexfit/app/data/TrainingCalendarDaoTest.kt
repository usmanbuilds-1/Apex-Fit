package com.apexfit.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.utils.CalendarScheduleKind
import com.apexfit.app.utils.TrainingCalendar
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainingCalendarDaoTest {
    @Test fun boundedSessionsAndActivePlanSlotsRefreshAfterEdits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val date = LocalDate.parse("2026-11-16")
            val month = YearMonth.from(date)
            dao.insertWorkoutPlan(WorkoutPlan(id = 81, name = "Rotation", goal = "Strength", isActive = true,
                cycleWeeks = 2, cycleAnchorMonday = date.toString()))
            val slot = PlanSession(91, 81, "Upper A", "Monday", "Chest", 1)
            dao.insertPlanSession(slot)
            dao.insertTrainingSession(TrainingSession("in", "2026-11-12", "Upper", true, 30, 3))
            dao.insertTrainingSession(TrainingSession("partial", "2026-11-12", "Extra", false, 15, 3, status = "partial"))
            dao.insertTrainingSession(TrainingSession("out", "2026-10-31", "Upper", true, 30, 3))
            val planFlow = dao.observeCalendarPlans()
            val logFlow = dao.observeCalendarSessions(month.atDay(1).toString(), month.atEndOfMonth().toString())
            val plans = withTimeout(10000) { planFlow.first() }
            val logs = withTimeout(10000) { logFlow.first() }
            assertEquals(setOf("in", "partial"), logs.map { it.id }.toSet())
            val calendar = TrainingCalendar.build(month, date, logs, plans)
            assertEquals(1, calendar.completedDays)
            assertEquals("Upper A", calendar.days[15].plannedLabel)
            assertEquals(CalendarScheduleKind.UNKNOWN, calendar.days[11].scheduleKind)
            dao.updatePlanSession(slot.copy(label = "Push A"))
            val revised = withTimeout(10000) { planFlow.first { it.single().sessions.single().label == "Push A" } }
            assertEquals("Push A", TrainingCalendar.build(month, date, logs, revised).days[15].plannedLabel)
            dao.deleteTrainingSession("partial")
            val afterDeletion = withTimeout(10000) { logFlow.first { it.size == 1 } }
            assertEquals("in", afterDeletion.single().id)
        } finally {
            db.close()
        }
    }
}
