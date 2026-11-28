package com.apexfit.app.utils

import com.apexfit.app.data.ActiveSet
import org.junit.Assert.*
import org.junit.Test

class ActiveSetDefaultsTest {
    @Test fun extraSetCopiesPrescriptionNotObservedEffortOrCompletion() {
        val previous = ActiveSet(3, 44.0, 8, rpe = 10, completed = true,
            restTakenSeconds = 90, completedAt = 1234, repsInReserve = 0, weightUnit = "lbs",
            advancedDetails = "prior bout")
        val extra = ActiveSetDefaults.extraSet(listOf(previous))
        assertEquals(4, extra.setNumber)
        assertEquals(44.0, extra.weight, 0.0)
        assertEquals(8, extra.reps)
        assertEquals("lbs", extra.weightUnit)
        assertEquals(0, extra.rpe)
        assertFalse(extra.completed)
        assertEquals(0L, extra.completedAt)
        assertEquals(0, extra.restTakenSeconds)
        assertNull(extra.advancedDetails)
    }

    @Test fun zeroRpeMeansUnratedNotVeryLowEffort() {
        assertEquals("Unrated", ProgressionEngine.getEffectivenessLabel(0))
        assertEquals("Very low effort", ProgressionEngine.getEffectivenessLabel(1))
        assertEquals("Maximal effort", ProgressionEngine.getEffectivenessLabel(10))
    }
}
