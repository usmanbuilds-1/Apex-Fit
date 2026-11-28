package com.apexfit.app.utils

import com.apexfit.app.data.AnalyticsWorkingSet
import java.time.LocalDate

/** Recorded mass convention is unknown on legacy rows; no inferred per-hand or assistance conversion. */
data class StrengthObservation(
    val setId: Long, val loadKg: Double?, val reps: Int,
    val fingerprint: String? = null, val completedAt: Long? = null,
    val capturedConfirmation: SetLoadConfirmation? = null
)

data class StrengthSessionPoint(
    val sessionId: String,
    val date: LocalDate,
    val partial: Boolean,
    val observations: List<StrengthObservation>
) {
    val heaviestRecorded: StrengthObservation? get() = observations.filter { it.loadKg != null }
        .maxWithOrNull(compareBy<StrengthObservation> { it.loadKg }.thenBy { it.reps }.thenByDescending { it.setId })
    val mostReps: StrengthObservation? get() = observations.maxWithOrNull(
        compareBy<StrengthObservation> { it.reps }.thenBy { it.loadKg ?: -1.0 }.thenByDescending { it.setId })
    val workingSets: Int get() = observations.size
    val totalReps: Long get() = observations.sumOf { it.reps.toLong() }
}

object StrengthAnalytics {
    fun calculate(rows: List<AnalyticsWorkingSet>, exerciseId: String, start: LocalDate, end: LocalDate,
                  nowMs: Long = System.currentTimeMillis()): List<StrengthSessionPoint> {
        val seen = mutableSetOf<Long>()
        return rows.asSequence().filter { row ->
            val date = runCatching { LocalDate.parse(row.analyticsDate) }.getOrNull()
            row.set.exerciseId == exerciseId && date != null && date in start..end &&
                (row.analyticsSessionCompleted || row.analyticsStatus == "partial") &&
                (row.analyticsCompletedAt ?: row.analyticsStartedAt ?: 0L) <= nowMs &&
                (row.set.setCompletedAt ?: 0L) <= nowMs &&
                TrainingEvidence.isWorkingSet(row.set) && (row.set.id == 0L || seen.add(row.set.id))
        }.groupBy { it.set.sessionId }.values.map { sessionRows ->
            val first = sessionRows.first()
            StrengthSessionPoint(first.set.sessionId, LocalDate.parse(first.analyticsDate),
                !first.analyticsSessionCompleted, sessionRows.map { row ->
                    val s = row.set
                    val kg = when (s.weightUnit.lowercase().trim()) {
                        "kg" -> s.weight
                        "lb", "lbs" -> s.weight / AppConstants.KG_TO_LBS
                        else -> null
                    }
                    StrengthObservation(s.id, kg?.takeIf { it.isFinite() }, s.reps,
                        if (s.advancedDetails == null) LoadConfirmations.fingerprint(s) else null, s.setCompletedAt,
                        SetObservationCodec.confirmation(s))
                })
        }.sortedWith(compareBy<StrengthSessionPoint> { it.date }.thenBy { it.sessionId })
    }
}
