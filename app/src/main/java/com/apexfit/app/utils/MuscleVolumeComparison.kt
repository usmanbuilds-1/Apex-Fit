package com.apexfit.app.utils

import com.apexfit.app.data.RichTrainingSession
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A closed calendar-date interval. No implied tracking coverage outside logged evidence. */
data class VolumeDateRange(val start: LocalDate, val end: LocalDate) {
    init { require(end >= start) }
    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1
}

data class VolumeComparisonWindows(
    val selected: VolumeDateRange,
    val current: VolumeDateRange?,
    val previous: VolumeDateRange?,
    val matchedElapsed: Boolean
) {
    val queryStart: LocalDate get() = previous?.start ?: selected.start
}

data class LoggedSetChange(val current: Int?, val previous: Int?) {
    val absolute: Long? get() = if (current != null && previous != null)
        current.toLong() - previous.toLong() else null
    val percent: Double? get() = if (previous != null && previous > 0 && absolute != null)
        absolute!!.toDouble() * 100.0 / previous else null
}

data class MusclePeriodChange(
    val direct: LoggedSetChange,
    val indirect: LoggedSetChange
)

data class MuscleVolumeComparison(
    val windows: VolumeComparisonWindows,
    val currentRows: List<MuscleVolumeRow>,
    val previousRows: List<MuscleVolumeRow>
) {
    // No qualifying attributed work anywhere in a range => evidence unavailable,
    // not a confirmed rest period. A missing muscle in an otherwise logged range
    // is zero LOGGED sets, not proof that the muscle was not trained.
    val currentHasLogs: Boolean get() = currentRows.isNotEmpty()
    val previousHasLogs: Boolean get() = previousRows.isNotEmpty()

    fun forMuscle(muscle: String): MusclePeriodChange {
        val current = currentRows.firstOrNull { it.muscle == muscle }
        val previous = previousRows.firstOrNull { it.muscle == muscle }
        return MusclePeriodChange(
            LoggedSetChange(
                if (currentHasLogs) current?.directSets ?: 0 else null,
                if (previousHasLogs) previous?.directSets ?: 0 else null
            ),
            LoggedSetChange(
                if (currentHasLogs) current?.secondarySets ?: 0 else null,
                if (previousHasLogs) previous?.secondarySets ?: 0 else null
            )
        )
    }
}

data class MuscleVolumeReport(
    val rows: List<MuscleVolumeRow>,
    val comparison: MuscleVolumeComparison
)

object MuscleVolumeComparisons {
    fun windows(period: MuscleVolumePeriod, anchor: LocalDate, today: LocalDate): VolumeComparisonWindows {
        val selected = period.window(minOf(anchor, today))
        val selectedRange = VolumeDateRange(selected.start, minOf(selected.endInclusive, today))
        val prior = period.window(period.shift(selected.start, -1))
        if (selected.endInclusive < today) {
            // Both calendar periods have ended. Show full periods even when their lengths differ.
            return VolumeComparisonWindows(
                selectedRange, selectedRange, VolumeDateRange(prior.start, prior.endInclusive), false
            )
        }

        // Today's log may still be changing. Compare only finished calendar days,
        // and truncate both sides to the shorter available duration.
        val elapsed = ChronoUnit.DAYS.between(selected.start, today)
        val priorLength = ChronoUnit.DAYS.between(prior.start, prior.endInclusive) + 1
        val matchedDays = minOf(elapsed, priorLength)
        if (matchedDays <= 0) return VolumeComparisonWindows(selectedRange, null, null, true)
        return VolumeComparisonWindows(
            selectedRange,
            VolumeDateRange(selected.start, selected.start.plusDays(matchedDays - 1)),
            VolumeDateRange(prior.start, prior.start.plusDays(matchedDays - 1)),
            true
        )
    }

    /** All projections consume the same bounded Room emission, including both intervals. */
    fun calculate(sessions: List<RichTrainingSession>, windows: VolumeComparisonWindows): MuscleVolumeReport {
        fun rows(range: VolumeDateRange?): List<MuscleVolumeRow> = range?.let {
            MuscleVolumeSummary.calculateWindow(sessions, it.start, it.end)
        }.orEmpty()
        val selected = rows(windows.selected)
        val current = if (windows.current == windows.selected) selected else rows(windows.current)
        return MuscleVolumeReport(selected, MuscleVolumeComparison(windows, current, rows(windows.previous)))
    }
}
