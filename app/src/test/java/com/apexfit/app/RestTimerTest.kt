package com.apexfit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerTest {
    @Test
    fun countdownUsesTheDeadlineAndNeverShowsNegativeTime() {
        assertEquals(60, remainingRestSeconds(61000L, 1000L))
        assertEquals(1, remainingRestSeconds(61000L, 60999L))
        assertEquals(0, remainingRestSeconds(61000L, 61000L))
        assertEquals(0, remainingRestSeconds(61000L, 90000L))
    }
}
