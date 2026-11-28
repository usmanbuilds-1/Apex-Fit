package com.apexfit.app.utils

import com.apexfit.app.data.AnalyticsWorkingSet
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class TrainingReportPeriod { WEEK, MONTH }

data class TrainingReportWindow(val start: LocalDate, val end: LocalDate) {
    init { require(start <= end) }
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
    operator fun contains(date: LocalDate): Boolean = date in start..end
}

data class TrainingReportRanges(val selected: TrainingReportWindow, val elapsed: TrainingReportWindow?,
                                val comparableCurrent: TrainingReportWindow?, val comparablePrior: TrainingReportWindow?) {
    val queryStart: LocalDate get() = comparablePrior?.start ?: selected.start
    val queryEnd: LocalDate get() = elapsed?.end ?: selected.start
}

data class PeriodTrainingMetrics(val window: TrainingReportWindow, val completedWorkouts: Int,
    val partialWorkouts: Int, val trainingDays: Int, val durationMinutes: Long?,
    val durationRecordedWorkouts: Int, val workingBouts: Int, val workingReps: Long,
    val workoutsWithWorkingBouts: Int, val adherence: ScheduleAdherenceSummary) {
    val loggedWorkouts: Int get() = completedWorkouts + partialWorkouts
}

data class TrainingPeriodReport(val ranges: TrainingReportRanges, val current: PeriodTrainingMetrics?,
    val matchedCurrent: PeriodTrainingMetrics?, val matchedPrior: PeriodTrainingMetrics?)

object TrainingPeriodReports {
    fun ranges(period: TrainingReportPeriod, anchor: LocalDate, today: LocalDate): TrainingReportRanges {
        val start = when (period) {
            TrainingReportPeriod.WEEK -> WorkoutSchedule.monday(anchor)
            TrainingReportPeriod.MONTH -> YearMonth.from(anchor).atDay(1)
        }
        val next = when (period) {
            TrainingReportPeriod.WEEK -> start.plusWeeks(1)
            TrainingReportPeriod.MONTH -> start.plusMonths(1)
        }
        val prior = when (period) {
            TrainingReportPeriod.WEEK -> start.minusWeeks(1)
            TrainingReportPeriod.MONTH -> start.minusMonths(1)
        }
        val selected = TrainingReportWindow(start, next.minusDays(1))
        val end = minOf(selected.end, today.minusDays(1))
        if (end < start) return TrainingReportRanges(selected, null, null, null)
        val elapsed = TrainingReportWindow(start, end)
        val matched = minOf(elapsed.days, ChronoUnit.DAYS.between(prior, start).toInt())
        return TrainingReportRanges(selected, elapsed,
            TrainingReportWindow(start, start.plusDays(matched.toLong() - 1)),
            TrainingReportWindow(prior, prior.plusDays(matched.toLong() - 1)))
    }

    fun calculate(ranges: TrainingReportRanges, days: List<TrainingCalendarDay>,
                  rows: List<AnalyticsWorkingSet>, today: LocalDate,
                  nowMs: Long = System.currentTimeMillis()): TrainingPeriodReport {
        require(days.map { it.date }.distinct().size == days.size)
        fun metrics(window: TrainingReportWindow?): PeriodTrainingMetrics? {
            if (window == null) return null
            val selected = days.filter { it.date in window && it.date < today }
            require(selected.size == window.days) { "Incomplete calendar input" }
            val sessions = selected.flatMap { day -> day.sessions.filter {
                it.date == day.date.toString() && (it.completed || it.status == "partial") &&
                    (it.completedAt ?: it.startedAt ?: 0L) <= nowMs
            } }.distinctBy { it.id }
            val byId = sessions.associateBy { it.id }
            val sets = rows.filter { row ->
                val session = byId[row.set.sessionId]
                session != null && row.analyticsDate == session.date &&
                    row.set.id > 0 && TrainingEvidence.isWorkingSet(row.set) &&
                    (row.set.setCompletedAt ?: 0L) <= nowMs &&
                    (row.set.advancedDetails == null || AdvancedSets.details(row.set) != null)
            }.distinctBy { it.set.id }.map { it.set }
            val durations = sessions.mapNotNull { it.durationMinutes.takeIf { n -> n > 0 } }
            return PeriodTrainingMetrics(window, sessions.count { it.completed },
                sessions.count { !it.completed && it.status == "partial" }, sessions.map { it.date }.distinct().size,
                durations.takeIf { it.isNotEmpty() }?.sumOf { it.toLong() }, durations.size,
                sets.size, sets.sumOf(AdvancedSets::totalReps), sets.map { it.sessionId }.distinct().size,
                ScheduleAdherence.summarize(selected, today))
        }
        return TrainingPeriodReport(ranges, metrics(ranges.elapsed), metrics(ranges.comparableCurrent),
            metrics(ranges.comparablePrior))
    }
}
