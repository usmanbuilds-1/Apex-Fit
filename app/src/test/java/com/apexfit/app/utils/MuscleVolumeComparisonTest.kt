package com.apexfit.app.utils

import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.RichTrainingSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MuscleVolumeComparisonTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun session(date: String, muscle: String, count: Int, secondary: List<String> = emptyList()) =
        RichTrainingSession(date, "Test", true, exercises = listOf(
            ExerciseLog(muscle, muscle, muscle, List(count) { ExerciseSet(weight = 20.0, reps = 8) },
                secondaryMuscles = secondary)
        ))

    @Test
    fun partialMonthUsesFinishedMatchedDaysAndKeepsTodayInSelectedTotals() {
        val today = d("2026-11-16")
        val windows = MuscleVolumeComparisons.windows(MuscleVolumePeriod.MONTH, today, today)
        assertEquals(VolumeDateRange(d("2026-11-01"), d("2026-11-15")), windows.current)
        assertEquals(VolumeDateRange(d("2026-10-01"), d("2026-10-15")), windows.previous)
        val report = MuscleVolumeComparisons.calculate(listOf(
            session("2026-10-01", "Chest", 4),
            session("2026-10-15", "Chest", 2),
            session("2026-10-16", "Chest", 99),
            session("2026-11-15", "Chest", 8),
            session("2026-11-16", "Chest", 5),
            session("2026-11-17", "Chest", 99)
        ), windows)
        assertEquals(13, report.rows.single().directSets)
        val change = report.comparison.forMuscle("chest").direct
        assertEquals(8, change.current)
        assertEquals(6, change.previous)
        assertEquals(2L, change.absolute)
        assertEquals(100.0 / 3, change.percent!!, 1e-9)
    }

    @Test
    fun currentLongMonthTruncatesBothWindowsToShortPreviousMonth() {
        val today = d("2024-03-31")
        val windows = MuscleVolumeComparisons.windows(MuscleVolumePeriod.MONTH, today, today)
        assertEquals(29L, windows.current!!.days)
        assertEquals(29L, windows.previous!!.days)
        assertEquals(d("2024-03-29"), windows.current!!.end)
        assertEquals(d("2024-02-29"), windows.previous!!.end)
        assertEquals(d("2024-03-31"), windows.selected.end)
    }

    @Test
    fun completedMonthsUseFullCalendarRangesEvenIfLengthsDiffer() {
        val windows = MuscleVolumeComparisons.windows(MuscleVolumePeriod.MONTH,
            d("2026-03-10"), d("2026-04-01"))
        assertFalse(windows.matchedElapsed)
        assertEquals(31L, windows.current!!.days)
        assertEquals(28L, windows.previous!!.days)
        assertEquals(windows.selected, windows.current)
    }

    @Test
    fun mondayAndFirstOfMonthHaveNoFinishedDaysToCompare() {
        for ((period, today) in listOf(
            MuscleVolumePeriod.WEEK to d("2026-11-16"),
            MuscleVolumePeriod.MONTH to d("2026-11-01"),
            MuscleVolumePeriod.YEAR to d("2026-01-01"))) {
            val windows = MuscleVolumeComparisons.windows(period, today, today)
            assertNull(windows.current)
            assertNull(windows.previous)
            assertEquals(today, windows.queryStart)
        }
    }

    @Test
    fun calendarWeekCrossesYearBoundaryCorrectly() {
        val today = d("2026-01-02")
        val windows = MuscleVolumeComparisons.windows(MuscleVolumePeriod.WEEK, today, today)
        assertEquals(VolumeDateRange(d("2025-12-29"), d("2026-01-01")), windows.current)
        assertEquals(VolumeDateRange(d("2025-12-22"), d("2025-12-25")), windows.previous)
    }

    @Test
    fun missingPriorWorkIsUnknownButZeroMuscleVolumeHasAbsoluteDeltaOnly() {
        val today = d("2026-11-16")
        val windows = MuscleVolumeComparisons.windows(MuscleVolumePeriod.MONTH, today, today)
        val current = session("2026-11-01", "Chest", 6)
        val noPrior = MuscleVolumeComparisons.calculate(listOf(current), windows).comparison
        assertFalse(noPrior.previousHasLogs)
        assertNull(noPrior.forMuscle("chest").direct.previous)
        assertNull(noPrior.forMuscle("chest").direct.absolute)
        val priorBack = session("2026-10-01", "Back", 3)
        val result = MuscleVolumeComparisons.calculate(listOf(current, priorBack), windows).comparison
        val chest = result.forMuscle("chest").direct
        assertEquals(0, chest.previous)
        assertEquals(6L, chest.absolute)
        assertNull(chest.percent)
        val back = result.forMuscle("back").direct
        assertEquals(-3L, back.absolute)
        assertEquals(-100.0, back.percent!!, 0.0)
    }

    @Test
    fun indirectAndDirectComparisonsStaySeparate() {
        val today = d("2026-11-16")
        val windows = MuscleVolumeComparisons.windows(MuscleVolumePeriod.MONTH, today, today)
        val report = MuscleVolumeComparisons.calculate(listOf(
            session("2026-10-01", "Chest", 4, listOf("Triceps")),
            session("2026-11-01", "Chest", 6, listOf("Triceps")),
            session("2026-11-02", "Triceps", 2)
        ), windows)
        val change = report.comparison.forMuscle("triceps")
        assertEquals(2L, change.direct.absolute)
        assertNull(change.direct.percent)
        assertEquals(2L, change.indirect.absolute)
        assertEquals(50.0, change.indirect.percent!!, 0.0)
    }

    @Test
    fun leapYearComparisonTruncatesPartialYearToPreviousYearLength() {
        val today = d("2024-12-31")
        val windows = MuscleVolumeComparisons.windows(MuscleVolumePeriod.YEAR, today, today)
        assertEquals(365L, windows.current!!.days)
        assertEquals(365L, windows.previous!!.days)
        assertEquals(d("2024-12-30"), windows.current!!.end)
        assertEquals(d("2023-12-31"), windows.previous!!.end)
    }
}
