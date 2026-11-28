package com.apexfit.app.utils

import com.apexfit.app.data.AnalyticsWorkingSet
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.TrainingSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TrainingPeriodReportsTest {
    @Test fun weekComparisonMatchesElapsedDaysAcrossYearBoundary() {
        val today = LocalDate.parse("2027-01-01")
        val r = TrainingPeriodReports.ranges(TrainingReportPeriod.WEEK, today, today)
        assertEquals(LocalDate.parse("2026-12-28"), r.selected.start)
        assertEquals(LocalDate.parse("2027-01-03"), r.selected.end)
        assertEquals(4, r.elapsed?.days)
        assertEquals(LocalDate.parse("2026-12-21"), r.comparablePrior?.start)
        assertEquals(LocalDate.parse("2026-12-24"), r.comparablePrior?.end)
    }

    @Test fun monthComparisonTruncatesToShorterLeapMonthAndExcludesToday() {
        val today = LocalDate.parse("2024-04-01")
        val r = TrainingPeriodReports.ranges(TrainingReportPeriod.MONTH, today.minusDays(1), today)
        assertEquals(31, r.elapsed?.days)
        assertEquals(29, r.comparableCurrent?.days)
        assertEquals(LocalDate.parse("2024-02-29"), r.comparablePrior?.end)
        assertNull(TrainingPeriodReports.ranges(TrainingReportPeriod.MONTH, today, today).elapsed)
        assertNull(TrainingPeriodReports.ranges(TrainingReportPeriod.MONTH, today.plusMonths(1), today).elapsed)
    }

    @Test fun actualSetsStayWithWorkoutDateAndAdvancedContinuationAddsRepsNotBouts() {
        val today = LocalDate.parse("2026-12-04")
        val r = TrainingPeriodReports.ranges(TrainingReportPeriod.WEEK, today, today)
        val actual = TrainingSession("actual", "2026-12-02", "Upper", true, 30, 3,
            planSessionId = 4, startedAt = 200, completedAt = 300)
        val partial = TrainingSession("partial", "2026-12-02", "Lower", false, 0, 3,
            status = "partial", startedAt = 400, completedAt = 500)
        val original = LocalDate.parse("2026-11-25") // Inside the matched Mon–Wed prior window
        val days = generateSequence(r.queryStart) { it.plusDays(1).takeIf { d -> d <= r.queryEnd } }
            .map { date ->
                val sessions = if (date.toString() == actual.date) listOf(actual, partial, actual) else emptyList()
                if (date == original) TrainingCalendarDay(date, sessions, CalendarScheduleKind.TRAINING,
                    "Upper", 4, "Plan", CalendarScheduleSource.RECORDED, "1:$date:4", rescheduledWorkout = actual)
                else TrainingCalendarDay(date, sessions, CalendarScheduleKind.UNKNOWN, null, null, null)
            }.toList()
        val parent = ExerciseSet(id = 1, sessionId = actual.id, exerciseId = "bench", weight = 20.0,
            reps = 8, rpe = 8, isWarmup = false, completed = true,
            advancedDetails = AdvancedSets.encode(20.0, AdvancedSetDetails(AdvancedSetKind.DROP,
                SetOutcome.UNKNOWN, listOf(SetContinuation(10.0, 5, 10, SetOutcome.UNKNOWN)))))
        fun row(s: ExerciseSet, date: String = actual.date) =
            AnalyticsWorkingSet(s, date, "Upper", true, "completed", 200, 300, emptyList())
        val rows = listOf(row(parent), row(parent), row(parent.copy(id = 2, isWarmup = true)),
            row(parent.copy(id = 3, sessionId = "missing")),
            row(parent.copy(id = 4, advancedDetails = "invalid")),
            row(parent.copy(id = 5), "2026-12-01"),
            row(parent.copy(id = 6, setCompletedAt = 1001)))
        val report = TrainingPeriodReports.calculate(r, days, rows, today, nowMs = 1000)
        val cur = requireNotNull(report.current)
        assertEquals(1, cur.completedWorkouts)
        assertEquals(1, cur.partialWorkouts)
        assertEquals(1, cur.trainingDays)
        assertEquals(30L, cur.durationMinutes)
        assertEquals(1, cur.durationRecordedWorkouts)
        assertEquals(1, cur.workingBouts)
        assertEquals(13L, cur.workingReps)
        assertEquals(1, cur.workoutsWithWorkingBouts)
        assertEquals(0, cur.adherence.completed)
        val prior = requireNotNull(report.matchedPrior)
        assertEquals(1, prior.adherence.completed)
        assertEquals(0, prior.loggedWorkouts)
        assertEquals(0, prior.workingBouts)
        assertNull(prior.durationMinutes)
    }
}
