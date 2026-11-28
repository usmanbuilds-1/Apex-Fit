package com.apexfit.app.utils

import java.time.LocalDate

/** Descriptive completion of recorded scheduled occurrences, not physiological compliance.
 * Excused dates are reported, but excluded from the eligible denominator.
 * Partial counts as a known non-full completion; unresolved remains unknown.
 */
data class ScheduleAdherenceSummary(
    val elapsedDays: Int,
    val knownScheduleDays: Int,
    val completed: Int,
    val partial: Int,
    val missed: Int,
    val excused: Int,
    val unresolved: Int
) {
    init {
        require(listOf(elapsedDays, knownScheduleDays, completed, partial, missed, excused, unresolved).all { it >= 0 })
        require(knownScheduleDays <= elapsedDays && recorded <= knownScheduleDays)
    }
    val recorded: Int get() = completed + partial + missed + excused + unresolved
    val eligible: Int get() = recorded - excused
    val resolvedEligible: Int get() = eligible - unresolved
    val unknownScheduleDays: Int get() = elapsedDays - knownScheduleDays

    private fun ratio(numerator: Int, denominator: Int): Double? =
        if (denominator > 0) numerator.toDouble() / denominator else null

    val scheduleCoverage: Double? get() = ratio(knownScheduleDays, elapsedDays)
    val outcomeCoverage: Double? get() = ratio(resolvedEligible, eligible)
    // Only a point estimate with all eligible outcomes resolved. Scope is recorded schedule only.
    val completionRate: Double? get() = if (unresolved == 0) ratio(completed, eligible) else null
    val completionLowerBound: Double? get() = ratio(completed, eligible)
    val completionUpperBound: Double? get() = ratio(completed + unresolved, eligible)
}

object ScheduleAdherence {
    fun summarize(days: List<TrainingCalendarDay>, today: LocalDate): ScheduleAdherenceSummary {
        // The calendar guarantees unique days. Reject duplicates rather than silently choosing evidence.
        require(days.map { it.date }.distinct().size == days.size)
        val elapsed = days.filter { it.date < today }
        val known = elapsed.filter { it.scheduleSource == CalendarScheduleSource.RECORDED &&
            (it.scheduleKind == CalendarScheduleKind.REST ||
                it.occurrenceStatus != RecordedOccurrenceStatus.NOT_APPLICABLE) }
        val counts = known.groupingBy { it.occurrenceStatus }.eachCount()
        fun count(status: RecordedOccurrenceStatus) = counts[status] ?: 0
        return ScheduleAdherenceSummary(elapsed.size, known.size,
            count(RecordedOccurrenceStatus.COMPLETED), count(RecordedOccurrenceStatus.PARTIAL),
            count(RecordedOccurrenceStatus.MISSED), count(RecordedOccurrenceStatus.EXCUSED),
            count(RecordedOccurrenceStatus.UNRESOLVED))
    }
}
