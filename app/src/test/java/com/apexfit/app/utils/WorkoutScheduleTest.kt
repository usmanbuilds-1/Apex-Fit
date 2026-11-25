package com.apexfit.app.utils

import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.WorkoutPlan
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class WorkoutScheduleTest {
    private val monday = LocalDate.parse("2026-11-23")
    private val plan = WorkoutPlan(id = 1, name = "A/B", goal = "General", isActive = true,
        cycleWeeks = 2, cycleAnchorMonday = monday.toString())
    private val slots = (1..2).flatMap { week ->
        listOf("Monday", "Wednesday", "Friday").mapIndexed { index, day ->
            PlanSession(id = (week * 10 + index).toLong(), planId = 1,
                label = if ((week + index) % 2 == 1) "Workout A" else "Workout B",
                day = day, focus = "Full body", cycleWeek = week)
        }
    }

    @Test
    fun exactAlternatingScheduleRepeatsOverCalendarWeeksWithoutRequiringCompletion() {
        val expected = listOf("Workout A", null, "Workout B", null, "Workout A", null, null,
            "Workout B", null, "Workout A", null, "Workout B", null, null)
        repeat(84) { offset ->
            val date = monday.plusDays(offset.toLong())
            assertEquals(expected[offset % 14], WorkoutSchedule.sessionForDate(plan, slots, date)?.label)
            assertEquals(offset / 7 % 2 + 1, WorkoutSchedule.cycleWeek(plan, date))
            assertEquals(3, WorkoutSchedule.trainingFrequency(plan, slots, date))
        }
    }

    @Test
    fun nextSessionCrossesSundayAndReturnsNextCycleWeek() {
        val next = requireNotNull(WorkoutSchedule.nextSession(plan, slots, monday.plusDays(6)))
        assertEquals(monday.plusDays(7), next.first)
        assertEquals("Workout B", next.second.label)
        val later = requireNotNull(WorkoutSchedule.nextSession(plan, slots, monday.plusDays(13)))
        assertEquals(monday.plusDays(14), later.first)
        assertEquals("Workout A", later.second.label)
    }

    @Test
    fun legacyWeeklyPlansNeverAlternate() {
        val legacy = plan.copy(cycleWeeks = 1, cycleAnchorMonday = null)
        val legacySlots = slots.filter { it.cycleWeek == 1 }
        repeat(100) { offset ->
            val date = monday.plusDays(offset.toLong())
            assertEquals(1, WorkoutSchedule.cycleWeek(legacy, date))
            assertEquals(WorkoutSchedule.sessionForDate(legacy, legacySlots, date.plusWeeks(1)),
                WorkoutSchedule.sessionForDate(legacy, legacySlots, date))
        }
    }

    @Test
    fun allRestWeekAndVariableFrequencyDoNotFallBackToWrongWeek() {
        val training = slots.filter { it.cycleWeek == 2 }
        assertTrue(WorkoutSchedule.weekSessions(plan, training, monday).isEmpty())
        assertEquals(0, WorkoutSchedule.trainingFrequency(plan, training, monday))
        assertNull(WorkoutSchedule.sessionForDate(plan, training, monday))
        assertEquals(monday.plusWeeks(1), WorkoutSchedule.nextSession(plan, training, monday)?.first)
        assertEquals(3, WorkoutSchedule.trainingFrequency(plan, training, monday.plusWeeks(1)))
        for (frequency in listOf(0, 7)) {
            val nutrition = MacroEngine.calcCycledTargets(2400, frequency, "Gain Muscle", 70.0, 175.0, "male")
            assertEquals(2400, nutrition.trainingDayCalories)
            assertEquals(2400, nutrition.restDayCalories)
        }
    }

    @Test
    fun malformedCycleRejectedBeforeDatabaseMutation() {
        assertNull(WorkoutSchedule.validationError(plan, slots))
        assertNotNull(WorkoutSchedule.validationError(plan, slots + slots.first().copy(id = 999)))
        assertNotNull(WorkoutSchedule.validationError(plan, slots + slots.first().copy(cycleWeek = 3)))
        assertNotNull(WorkoutSchedule.validationError(plan.copy(cycleWeeks = 0), slots))
        assertNotNull(WorkoutSchedule.validationError(plan.copy(cycleAnchorMonday = "2026-11-24"), slots))
        assertNotNull(WorkoutSchedule.validationError(plan.copy(cycleAnchorMonday = "garbage"), slots))
    }

    @Test
    fun seededCalendarScenariosAcrossYearsAndCycleLengths() {
        val random = Random(20261124)
        repeat(1500) {
            val length = random.nextInt(1, 13)
            val anchor = WorkoutSchedule.monday(LocalDate.of(1990 + random.nextInt(70), 1, 1)
                .plusDays(random.nextLong(365)))
            val offset = random.nextLong(-2000, 2000)
            val dayOffset = random.nextLong(0, 7)
            val date = anchor.plusWeeks(offset).plusDays(dayOffset)
            val rotating = plan.copy(cycleWeeks = length, cycleAnchorMonday = anchor.toString())
            val expected = ((offset % length + length) % length).toInt() + 1
            assertEquals(expected, WorkoutSchedule.cycleWeek(rotating, date))
            assertEquals(expected, WorkoutSchedule.cycleWeek(rotating, date.plusWeeks(length.toLong())))
            assertEquals(anchor, WorkoutSchedule.monday(anchor.plusDays(6)))
        }
    }
}
