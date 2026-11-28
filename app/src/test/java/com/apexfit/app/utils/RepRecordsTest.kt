package com.apexfit.app.utils

import java.time.LocalDate
import com.apexfit.app.data.ExerciseSet
import org.junit.Assert.*
import org.junit.Test

class RepRecordsTest {
    private fun point(id: Long, kg: Double, reps: Int = 8, day: Int = 1, time: Long? = null) =
        StrengthSessionPoint("session-$id", LocalDate.of(2026, 11, day), false,
            listOf(StrengthObservation(id, kg, reps, "fingerprint-$id", time)))
    private fun confirmations(points: List<StrengthSessionPoint>) = points.flatMap { it.observations }
        .associate { it.setId to SetLoadConfirmation(it.setId, it.fingerprint!!, RecordedLoadBasis.TOTAL, "Gym A") }

    @Test fun exactRepBestAndStrictImprovementsRecomputeAfterDeletion() {
        val points = listOf(point(1, 50.0, day = 1), point(2, 55.0, day = 2),
            point(3, 55.0, day = 3), point(4, 60.0, reps = 5, day = 4))
        val c = confirmations(points)
        val result = RepRecords.calculate(points, c)
        assertEquals(listOf(1L, 2L), result.events.filter { it.key.reps == 8 }.map { it.setId })
        assertEquals(2, result.best.size)
        assertEquals(3L, RepRecords.calculate(points.filterNot { it.sessionId == "session-2" }, c)
            .best.first { it.key.reps == 8 }.setId)
        assertEquals(1L, RepRecords.calculate(points.filterNot { it.sessionId in setOf("session-2", "session-3") }, c)
            .best.first { it.key.reps == 8 }.setId)
    }

    @Test fun basisAndSetupAreDistinctAndUnconfirmedExcluded() {
        val points = (1L..5L).map { point(it, 50.0 + it, day = it.toInt()) }
        val c = confirmations(points).toMutableMap()
        c[2] = c.getValue(2).copy(basis = RecordedLoadBasis.PER_HAND)
        c[3] = c.getValue(3).copy(basis = RecordedLoadBasis.MACHINE_STACK)
        c[4] = c.getValue(4).copy(setup = "Gym B")
        c.remove(5)
        assertEquals(4, RepRecords.calculate(points, c).best.size)
        assertFalse(RepRecords.calculate(points, c).events.any { it.setId == 5L })
    }

    @Test fun unknownSameDayOrderCollapsesToBestAndKnownOrderReplays() {
        val unknown = listOf(point(1, 50.0), point(2, 55.0), point(3, 55.0))
        val records = RepRecords.calculate(unknown, confirmations(unknown)).events
        assertEquals(listOf(2L), records.map { it.setId })
        assertTrue(records.single().chronologyLimited)
        val known = listOf(point(1, 50.0, time = 1000), point(2, 55.0, time = 2000))
        assertEquals(listOf(1L, 2L), RepRecords.calculate(known, confirmations(known)).events.map { it.setId })
    }

    @Test fun changedSetFingerprintCannotReuseOldConfirmation() {
        val p = point(1, 50.0)
        val c = confirmations(listOf(p))
        val changed = p.copy(observations = listOf(p.observations.single().copy(loadKg = 60.0, fingerprint = "edited")))
        assertTrue(RepRecords.calculate(listOf(changed), c).events.isEmpty())
        val set = ExerciseSet(weight = 50.0, reps = 8)
        assertNotEquals(LoadConfirmations.fingerprint(set), LoadConfirmations.fingerprint(set.copy(reps = 9)))
    }

    @Test fun gramPrecisionAvoidsFalseTieAndZeroLoadExcluded() {
        val points = listOf(point(1, 50.0), point(2, 50.0001, day = 2), point(3, 0.0, reps = 20, day = 3))
        assertEquals(listOf(1L), RepRecords.calculate(points, confirmations(points)).events.map { it.setId })
    }

    @Test fun confirmationCodecRejectsMalformedAndConflictingValues() {
        val c = SetLoadConfirmation(1, "a".repeat(64), RecordedLoadBasis.TOTAL, "Gym A")
        assertEquals(c, LoadConfirmations.decode(LoadConfirmations.encode(c)))
        assertNull(LoadConfirmations.decode("1|bad|TOTAL|Gym A"))
        assertTrue(LoadConfirmations.decodeAll(setOf(LoadConfirmations.encode(c),
            LoadConfirmations.encode(c.copy(basis = RecordedLoadBasis.PER_HAND)))).isEmpty())
    }
}
