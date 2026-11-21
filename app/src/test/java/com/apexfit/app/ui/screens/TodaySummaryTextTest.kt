package com.apexfit.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodaySummaryTextTest {
    @Test
    fun weeklySummaryUsesTheActivePlanTargetWhenAvailable() {
        assertEquals("1 of 4 sessions this week", todayTrainingWeekSummary(1, 4))
        assertEquals("0 of 1 session this week", todayTrainingWeekSummary(0, 1))
    }

    @Test
    fun weeklySummaryFallsBackToCompletedSessionsWithoutAPlanTarget() {
        assertEquals("1 session completed this week", todayTrainingWeekSummary(1, 0))
        assertEquals("3 sessions completed this week", todayTrainingWeekSummary(3, 0))
    }

    @Test
    fun exerciseCountIsOnlyShownForARealScheduledCount() {
        assertNull(todayExerciseCountLabel(0))
        assertEquals("1 exercise", todayExerciseCountLabel(1))
        assertEquals("5 exercises", todayExerciseCountLabel(5))
    }
}
