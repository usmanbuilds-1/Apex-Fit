package com.apexfit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerElapsedTest {
    @Test
    fun elapsedRestUsesActualMonotonicTimeRatherThanTimerTarget() {
        val startedAtElapsedRealtime = 12_000L
        val dismissedAtElapsedRealtime = 43_900L

        // This is the same elapsed value whether the user extended, shortened, or skipped
        // the remaining countdown: only time actually spent in the rest interval is saved.
        assertEquals(31, elapsedRestSeconds(startedAtElapsedRealtime, dismissedAtElapsedRealtime))
    }

    @Test
    fun immediateSkipRecordsZeroAndClockRollbackCannotProduceNegativeRest() {
        assertEquals(0, elapsedRestSeconds(43_900L, 43_900L))
        assertEquals(0, elapsedRestSeconds(43_900L, 12_000L))
    }
}
