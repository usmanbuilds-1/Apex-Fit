package com.apexfit.app.utils

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class StrengthDashboardPerformanceTest {
    private val base = LocalDate.of(2020,1,1)

    @Test fun binaryPriorWeightLookupMatchesLinearReferenceAcrossLongHistory() {
        val days = (0..3000 step 3).map { base.plusDays(it.toLong()) to (60.0 + it / 100.0) }
        for (offset in -3..3005) {
            val date = base.plusDays(offset.toLong())
            assertEquals("offset $offset", days.indexOfLast { it.first < date },
                EstimatedStrength.priorDayIndex(days, date))
        }
        assertEquals(-1, EstimatedStrength.priorDayIndex(emptyList(), base))
    }

    @Test fun switchingRangeDoesNotChangeAllTimeRecordsOrLeakOlderPoints() {
        val points = listOf(
            StrengthSessionPoint("old", base, false, listOf(StrengthObservation(1, 60.0, 5, "a"))),
            StrengthSessionPoint("new", base.plusDays(90), false, listOf(StrengthObservation(2, 50.0, 5, "b")))
        )
        val confirmations = mapOf(
            1L to SetLoadConfirmation(1, "a", RecordedLoadBasis.TOTAL, "Gym", true),
            2L to SetLoadConfirmation(2, "b", RecordedLoadBasis.TOTAL, "Gym", true))
        val records = RepRecords.calculate(points, confirmations)
        val estimates = EstimatedStrength.calculate(points, confirmations, emptyList())
        val dashboard = StrengthDashboardSnapshot(points, records, estimates, confirmations)
        assertEquals(listOf("new"), dashboard.historyFrom(base.plusDays(1)).map { it.sessionId })
        assertEquals(listOf("new"), dashboard.estimatesFrom(base.plusDays(1)).map { it.sessionId })
        assertEquals(listOf(1L), dashboard.records.best.map { it.setId })
        assertEquals(points, dashboard.historyFrom(base))
        assertTrue(dashboard.historyFrom(base.plusDays(91)).isEmpty())
    }
}
