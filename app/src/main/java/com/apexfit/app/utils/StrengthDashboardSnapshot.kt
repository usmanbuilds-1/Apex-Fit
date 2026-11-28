package com.apexfit.app.utils

import java.time.LocalDate

/** All three projections originate from the same saved-history snapshot. */
data class StrengthDashboardSnapshot(
    val points: List<StrengthSessionPoint>,
    val records: RepRecordProjection,
    val estimates: List<EstimatedStrengthPoint>,
    val confirmations: Map<Long, SetLoadConfirmation>
) {
    fun historyFrom(start: LocalDate) = points.filter { it.date >= start }
    fun estimatesFrom(start: LocalDate) = estimates.filter { it.date >= start }
}
