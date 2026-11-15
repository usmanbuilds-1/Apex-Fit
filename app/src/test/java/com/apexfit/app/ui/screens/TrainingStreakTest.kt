package com.apexfit.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TrainingStreakTest {
    private val today = LocalDate.of(2026, 11, 15) // Sunday

    @Test
    fun weeklyTrainingCountIgnoresGapsAndCountsOnlyCurrentWeek() {
        assertEquals(0, currentWeekTrainingCount(emptySet(), today))

        // Monday and Thursday both count even though rest days exist between them.
        assertEquals(
            2,
            currentWeekTrainingCount(
                setOf(
                    LocalDate.of(2026, 11, 9),
                    LocalDate.of(2026, 11, 12)
                ),
                today
            )
        )

        // A session from the previous week does not inflate this week.
        assertEquals(
            1,
            currentWeekTrainingCount(
                setOf(
                    LocalDate.of(2026, 11, 8),
                    LocalDate.of(2026, 11, 10)
                ),
                today
            )
        )

        // Future dates are ignored.
        assertEquals(
            1,
            currentWeekTrainingCount(
                setOf(
                    LocalDate.of(2026, 11, 14),
                    LocalDate.of(2026, 11, 16)
                ),
                today
            )
        )
    }
}
