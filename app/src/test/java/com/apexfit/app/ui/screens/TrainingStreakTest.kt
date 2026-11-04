package com.apexfit.app.ui.screens

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingStreakTest {
    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun streakIncludesTodayOrYesterdayAndStopsAtTheFirstGap() {
        assertEquals(0, currentTrainingStreak(emptySet(), today))
        assertEquals(0, currentTrainingStreak(setOf(today.minusDays(2)), today))
        assertEquals(2, currentTrainingStreak(setOf(today, today.minusDays(1), today.minusDays(3)), today))
        assertEquals(2, currentTrainingStreak(setOf(today.minusDays(1), today.minusDays(2)), today))
    }
}
