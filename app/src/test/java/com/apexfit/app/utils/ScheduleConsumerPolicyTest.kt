package com.apexfit.app.utils

import com.apexfit.app.data.PlanSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ScheduleConsumerPolicyTest {
    private val date = LocalDate.parse("2026-12-01")
    private val slot = PlanSession(12, 9, "Upper", "Monday", "Upper", 1)
    private fun choice(kind: TodayWorkoutKind, session: PlanSession? = null, day: LocalDate = date) =
        TodayWorkoutChoice(day, session, kind)

    @Test fun nutritionUsesResolvedScheduleOrActualCompletedWorkout() {
        assertFalse(ScheduleConsumerPolicy.isTrainingDay(date, choice(TodayWorkoutKind.DEFERRED), 0))
        assertTrue(ScheduleConsumerPolicy.isTrainingDay(date, choice(TodayWorkoutKind.DEFERRED), 1))
        assertTrue(ScheduleConsumerPolicy.isTrainingDay(date, choice(TodayWorkoutKind.RESERVED, slot), 0))
        assertTrue(ScheduleConsumerPolicy.isTrainingDay(date, choice(TodayWorkoutKind.REGULAR, slot), 0))
        assertFalse(ScheduleConsumerPolicy.isTrainingDay(date, choice(TodayWorkoutKind.RESERVED, slot, date.minusDays(1)), 0))
        assertFalse(ScheduleConsumerPolicy.isTrainingDay(date, null, 0))
    }

    @Test fun remindersUseCurrentUncompletedActionableSessionAndRejectStaleDelivery() {
        assertEquals("Upper", ScheduleConsumerPolicy.readinessSession(date, choice(TodayWorkoutKind.RESERVED, slot), 0))
        assertNull(ScheduleConsumerPolicy.readinessSession(date, choice(TodayWorkoutKind.DEFERRED), 0))
        assertNull(ScheduleConsumerPolicy.readinessSession(date, choice(TodayWorkoutKind.REVIEW, slot), 0))
        assertNull(ScheduleConsumerPolicy.readinessSession(date, choice(TodayWorkoutKind.REGULAR, slot), 1))
        assertNull(ScheduleConsumerPolicy.readinessSession(date, choice(TodayWorkoutKind.REGULAR, slot, date.minusDays(1)), 0))
        assertTrue(ScheduleConsumerPolicy.sameDayNotification(date.toString(), date))
        assertFalse(ScheduleConsumerPolicy.sameDayNotification(date.minusDays(1).toString(), date))
        assertFalse(ScheduleConsumerPolicy.sameDayNotification(null, date))
    }
}
