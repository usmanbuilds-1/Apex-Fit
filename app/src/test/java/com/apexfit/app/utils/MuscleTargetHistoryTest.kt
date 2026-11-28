package com.apexfit.app.utils

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MuscleTargetHistoryTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun initialTargetStartsNextMondayWithoutBackfill() {
        val history = MuscleTargetHistory.schedule(emptyList(), "Chest", 8, d("2026-11-18"))
        assertNull(MuscleTargetHistory.resolve(history, "chest", d("2026-11-22")))
        assertEquals(8, MuscleTargetHistory.resolve(history, "chest", d("2026-11-23"))!!.target)
    }

    @Test
    fun changeToTenPreservesPastAndCurrentTargets() {
        var history = MuscleTargetHistory.schedule(emptyList(), "Chest", 8, d("2026-11-18"))
        history = MuscleTargetHistory.schedule(history, "chest", 10, d("2026-11-25"))
        assertEquals(8, MuscleTargetHistory.resolve(history, "Chest", d("2026-11-29"))!!.target)
        assertEquals(10, MuscleTargetHistory.resolve(history, "Chest", d("2026-11-30"))!!.target)
        assertEquals(8, history.first().target)
    }

    @Test
    fun multipleEditsReplaceOnlyPendingRevisionAndKeepOtherMuscles() {
        var history = MuscleTargetHistory.schedule(emptyList(), "Lats", 8, d("2026-11-18"))
        history = MuscleTargetHistory.schedule(history, "Biceps", 5, d("2026-11-18"))
        history = MuscleTargetHistory.schedule(history, "Back", 11, d("2026-11-21"))
        assertEquals(2, history.size)
        assertEquals(11, MuscleTargetHistory.resolve(history, "back", d("2026-11-23"))!!.target)
        assertEquals(5, MuscleTargetHistory.resolve(history, "biceps", d("2026-11-23"))!!.target)
    }

    @Test
    fun clearAndZeroAreDistinctAndSurviveEncoding() {
        var history = MuscleTargetHistory.schedule(emptyList(), "Chest", 0, d("2026-11-18"))
        history = MuscleTargetHistory.schedule(history, "Chest", null, d("2026-11-25"))
        val restored = MuscleTargetHistory.decode(MuscleTargetHistory.encode(history))
        assertEquals(history, restored)
        assertEquals(0, MuscleTargetHistory.resolve(restored, "chest", d("2026-11-29"))!!.target)
        val cleared = MuscleTargetHistory.resolve(restored, "chest", d("2026-11-30"))
        assertNotNull(cleared)
        assertNull(cleared!!.target)
    }

    @Test
    fun validationRejectsMalformedOrNonMuscleTargets() {
        for ((muscle, target) in listOf("cardio" to 5, "chest" to -1, "chest" to 1000)) {
            assertTrue(runCatching { MuscleTargetHistory.schedule(emptyList(), muscle, target, d("2026-11-18")) }.isFailure)
        }
        assertEquals(emptyList<MuscleTargetRevision>(), MuscleTargetHistory.decode(
            setOf("chest|2026-11-18|2", "chest|bad|2", "chest|2026-11-23|-1",
                "cardio|2026-11-23|4", "chest|2026-11-23|8", "chest|2026-11-23|9")))
    }

    @Test
    fun mondayRolloverAndYearBoundaryAreExplicit() {
        assertEquals(d("2027-01-04"), MuscleTargetHistory.nextWeek(d("2027-01-01")))
        assertEquals(d("2027-01-11"), MuscleTargetHistory.nextWeek(d("2027-01-04")))
    }
}
