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

    @Test
    fun restRecoveryUsesVerifiedMonotonicClockAndCapsExpiredObservation() {
        val rest = com.apexfit.app.data.ActiveRestCheckpoint("bench", 0, 1_000L, 61_000L, 7, 60)
        val state = com.apexfit.app.utils.WorkoutResumeState
        assertEquals(24, state.observedRestSeconds(rest, 25_999L, 7))
        assertEquals(60, state.observedRestSeconds(rest, 900_000L, 7))
        assertEquals(null, state.observedRestSeconds(rest, 25_000L, 8))
        assertEquals(null, state.observedRestSeconds(rest, 25_000L, null))
        assertEquals(null, state.observedRestSeconds(rest, 999L, 7))
        assertEquals(24, state.observedRestSeconds(rest.copy(bootCount = null), 25_000L, null, liveInProcess = true))
    }
}
