package com.apexfit.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import com.apexfit.app.data.RichTrainingSession

class WeeklyTrainingSessionCountTest {
    private val today = LocalDate.of(2026, 11, 15) // Sunday

    @Test
    fun countsCompletedSessionsNotUniqueDatesAndExcludesOutsideWeek() {
        val sessions = listOf(
            RichTrainingSession(date = "2026-11-09", sessionType = "Upper A", completed = true),
            RichTrainingSession(date = "2026-11-09", sessionType = "Lower A", completed = true),
            RichTrainingSession(date = "2026-11-12", sessionType = "Upper B", completed = true),
            RichTrainingSession(date = "2026-11-08", sessionType = "Previous week", completed = true),
            RichTrainingSession(date = "2026-11-16", sessionType = "Future", completed = true),
            RichTrainingSession(date = "2026-11-14", sessionType = "Incomplete", completed = false)
        )

        assertEquals(3, currentWeekTrainingSessionCount(sessions, today))
    }

    @Test
    fun ignoresMalformedDates() {
        assertEquals(
            0,
            currentWeekTrainingSessionCount(
                listOf(RichTrainingSession(date = "not-a-date", sessionType = "Unknown", completed = true)),
                today
            )
        )
    }
}
