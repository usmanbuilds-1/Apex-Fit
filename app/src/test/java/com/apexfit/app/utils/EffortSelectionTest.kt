package com.apexfit.app.utils

import org.junit.Assert.*
import org.junit.Test

class EffortSelectionTest {
    @Test fun noSelectionDoesNotBecomeRpeEightOrTwoRir() {
        assertFalse(EffortSelection.isRated(EffortSelection.UNRATED))
        assertEquals(0, EffortSelection.rpe(EffortSelection.UNRATED))
        assertNull(EffortSelection.actualRir(EffortSelection.UNRATED))
        assertNull(EffortSelection.actualRir(6))
    }

    @Test fun explicitSelectionPreservesZeroAndTheFivePlusBucket() {
        assertEquals(10, EffortSelection.rpe(0))
        assertEquals(0, EffortSelection.actualRir(0))
        assertEquals(8, EffortSelection.rpe(2))
        assertEquals(6, EffortSelection.rpe(5))
        assertEquals(5, EffortSelection.actualRir(5))
    }
}
