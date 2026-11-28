package com.apexfit.app.utils

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ScheduleAdherenceTest {
    @Test fun unresolvedProducesBoundsNotPointEstimate() {
        val s = ScheduleAdherenceSummary(20, 15, 5, 1, 1, 1, 2)
        assertEquals(10, s.recorded)
        assertEquals(9, s.eligible)
        assertEquals(7, s.resolvedEligible)
        assertEquals(5, s.unknownScheduleDays)
        assertNull(s.completionRate)
        assertEquals(5.0 / 9, requireNotNull(s.completionLowerBound), 1e-10)
        assertEquals(7.0 / 9, requireNotNull(s.completionUpperBound), 1e-10)
        assertEquals(7.0 / 9, requireNotNull(s.outcomeCoverage), 1e-10)
        assertEquals(0.75, requireNotNull(s.scheduleCoverage), 1e-10)
    }

    @Test fun completeEvidenceExcludesExcusedAndCountsPartialAsNotFull() {
        val s = ScheduleAdherenceSummary(10, 10, 4, 1, 1, 2, 0)
        assertEquals(4.0 / 6, requireNotNull(s.completionRate), 1e-10)
        assertEquals(s.completionRate, s.completionLowerBound)
        assertEquals(s.completionRate, s.completionUpperBound)
        assertEquals(1.0, requireNotNull(s.outcomeCoverage), 0.0)
    }

    @Test fun noEligibleDenominatorIsNotZeroOrPerfect() {
        for (s in listOf(ScheduleAdherenceSummary(0, 0, 0, 0, 0, 0, 0),
            ScheduleAdherenceSummary(7, 0, 0, 0, 0, 0, 0),
            ScheduleAdherenceSummary(7, 7, 0, 0, 0, 7, 0))) {
            assertNull(s.completionRate)
            assertNull(s.completionLowerBound)
            assertNull(s.completionUpperBound)
            assertNull(s.outcomeCoverage)
        }
        val unresolved = ScheduleAdherenceSummary(7, 7, 0, 0, 0, 0, 7)
        assertEquals(0.0, requireNotNull(unresolved.completionLowerBound), 0.0)
        assertEquals(1.0, requireNotNull(unresolved.completionUpperBound), 0.0)
        assertNull(unresolved.completionRate)
    }

    @Test fun summaryExcludesTodayFutureAndUnknownSchedule() {
        val today = LocalDate.parse("2026-11-16")
        fun day(offset: Long, kind: CalendarScheduleKind, source: CalendarScheduleSource,
                decision: OccurrenceDecision? = null): TrainingCalendarDay {
            val date = today.plusDays(offset)
            val id = if (kind == CalendarScheduleKind.TRAINING && source == CalendarScheduleSource.RECORDED)
                "1:$date:2" else null
            return TrainingCalendarDay(date, emptyList(), kind, null, 2, null, source, id, decision)
        }
        val days = listOf(
            day(-4, CalendarScheduleKind.UNKNOWN, CalendarScheduleSource.UNKNOWN),
            day(-3, CalendarScheduleKind.REST, CalendarScheduleSource.RECORDED),
            day(-2, CalendarScheduleKind.TRAINING, CalendarScheduleSource.RECORDED, OccurrenceDecision.MISSED),
            day(-1, CalendarScheduleKind.TRAINING, CalendarScheduleSource.RECORDED),
            day(0, CalendarScheduleKind.TRAINING, CalendarScheduleSource.LIVE_PROJECTION),
            day(1, CalendarScheduleKind.TRAINING, CalendarScheduleSource.LIVE_PROJECTION))
        val s = ScheduleAdherence.summarize(days, today)
        assertEquals(4, s.elapsedDays)
        assertEquals(3, s.knownScheduleDays)
        assertEquals(2, s.eligible)
        assertEquals(1, s.missed)
        assertEquals(1, s.unresolved)
        assertNull(s.completionRate)
        assertTrue(runCatching { ScheduleAdherence.summarize(days + days.first(), today) }.isFailure)
    }
}
