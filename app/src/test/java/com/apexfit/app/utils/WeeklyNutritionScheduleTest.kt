package com.apexfit.app.utils

import com.apexfit.app.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WeeklyNutritionScheduleTest {
    private val sunday = LocalDate.parse("2026-11-29")
    private val target = sunday.plusDays(2)
    private val plan = WorkoutPlan(id = 9, name = "Upper", goal = "Strength", isActive = true)
    private val slot = PlanSession(12, 9, "Upper", "Sunday", "Upper", 1)
    private val plans = listOf(CalendarPlanSnapshot(plan, listOf(slot)))
    private val revision = ScheduleRevisionSnapshot(
        ScheduleRevision(1, "2026-11-23", 100, 9, "Upper", 1, null),
        listOf(ScheduleRevisionSlot(1, 12, 1, 7, "Upper", true)))
    private val row = ScheduleMoveReservation("1:$sunday:12", sunday.toString(), target.toString(), 1, 12, 200)

    @Test fun crossWeekMoveChangesProjectedDaysNotUnderlyingPlan() {
        fun project(date: LocalDate) = requireNotNull(WeeklyNutritionSchedule.project(
            date, plans, listOf(revision), listOf(row), emptyList(), emptyList(), 1, 300))
        assertEquals(emptySet<LocalDate>(), project(sunday.minusDays(1)).trainingDates)
        assertEquals(setOf(target, sunday.plusWeeks(1)), project(target).trainingDates)
    }

    @Test fun consumedReservationRemainsRepresentedByVerifiedAssociation() {
        val saved = TrainingSession("saved", target.toString(), "Upper", true, 30, 3,
            planSessionId = 12, startedAt = 300, completedAt = 400)
        val association = RescheduledWorkoutSnapshot(
            ScheduleWorkoutAssociation(row.occurrenceId, sunday.toString(), saved.id, 400), saved)
        val projection = requireNotNull(WeeklyNutritionSchedule.project(
            target.plusDays(1), plans, listOf(revision), emptyList(), listOf(saved), listOf(association), 1, 500))
        assertEquals(setOf(target, sunday.plusWeeks(1)), projection.trainingDates)
    }

    @Test fun projectedCaloriesSumExactlyForAllTrainingDayCounts() {
        val monday = LocalDate.parse("2026-11-30")
        val dates = (0L..6L).map(monday::plusDays)
        for (base in listOf(0, 1100, 1901, 2400, 2501, 3000)) {
            for (count in 0..7) {
                for (goal in listOf("lose fat", "build muscle", "recomposition", "maintain")) {
                    val projection = WeeklyNutritionProjection(monday, dates.take(count).toSet())
                    val daily = dates.map { MacroEngine.calcProjectedDayTargets(base, projection, it,
                        goal, 61.0, 180.0, "male") }
                    assertEquals(base.toLong() * 7, daily.sumOf { it.calories.toLong() })
                    assertTrue(daily.all { it.calories >= 0 && it.carbs >= 0 })
                    assertEquals(1, daily.map { it.protein }.distinct().size)
                    assertEquals(1, daily.map { it.fat }.distinct().size)
                    if (base < 1200) assertTrue(daily.all { it.calories == base })
                }
            }
        }
    }

    @Test fun absentPlanDoesNotInventWeekdays() {
        assertNull(WeeklyNutritionSchedule.project(target, emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), 4, 500))
    }
}
