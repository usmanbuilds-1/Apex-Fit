package com.apexfit.app.utils

import com.apexfit.app.data.*

object FatigueEngine {
    // ── RECENT-TO-BASELINE LOAD RATIO ─────────────────────────
    // This is a descriptive workload heuristic. Ratio cutoffs are not validated
    // as individual injury-risk thresholds and must not be presented that way.
    fun calcFatigueToFitness(trainingLog: List<TrainingSession>): FatigueResult =
        calcFatigueToFitnessRatio(trainingLog)

    fun calcFatigueToFitnessRatio(trainingLog: List<TrainingSession>): FatigueResult {
        val today = java.time.LocalDate.parse(getCurrentDate())
        val seenIds = mutableSetOf<Long>()
        val dailySets = TrainingEvidence.sessions(trainingLog, today.minusDays(27), today)
            .groupBy { java.time.LocalDate.parse(it.date) }
            .toSortedMap()
            .mapValues { (_, sessions) ->
                sessions.sumOf { session -> session.exercises.sumOf { exercise ->
                    exercise.sets.count { TrainingEvidence.isWorkingSet(it) &&
                        (it.id == 0L || seenIds.add(it.id)) }
                } }
            }
        val acuteLoad = dailySets.filterKeys { it >= today.minusDays(6) }.values.sum().toDouble()
        // Compare logged set counts with three non-overlapping prior weeks.
        // Each baseline week needs at least one logged working set. Missing logs
        // cannot establish actual rest or complete training coverage.
        val baselineWeeks = (1..3).map { week ->
            val end = today.minusDays(7L * week)
            dailySets.filterKeys { it in end.minusDays(6)..end }.values.sum()
        }
        val chronicLoad = if (baselineWeeks.all { it > 0 }) baselineWeeks.average() else 0.0
        val ratio = if (chronicLoad > 0.0) acuteLoad / chronicLoad else null

        val status: String
        val statusLabel: String
        val recommendation: String
        when {
            ratio == null -> { status = "unknown"; statusLabel = "No Data"; recommendation = "Log working sets in each of the previous three weeks to compare logged volume." }
            ratio < 0.6 -> { status = "detraining"; statusLabel = "Well Below Baseline"; recommendation = "Recent logged set volume is well below your previous 3-week baseline. That can be intentional during recovery or a deload." }
            ratio < 0.8 -> { status = "low"; statusLabel = "Below Baseline"; recommendation = "Recent logged set volume is below your previous 3-week baseline. Missing workouts may affect this comparison." }
            ratio <= 1.3 -> { status = "optimal"; statusLabel = "Near Baseline"; recommendation = "Recent logged set volume is close to your previous 3-week baseline. This is not a measure of recovery." }
            ratio <= 1.5 -> { status = "high"; statusLabel = "Above Baseline"; recommendation = "Recent logged set volume is above your previous 3-week baseline. Use performance and how you feel to guide changes." }
            else -> { status = "danger"; statusLabel = "Well Above Baseline"; recommendation = "Recent logged set volume is well above your previous 3-week baseline. This alone does not establish a need for a deload." }
        }
        return FatigueResult(ratio, status, statusLabel, recommendation, acuteLoad, chronicLoad)
    }

    // ── MUSCLE HEATMAP ────────────────────────────────────────
    fun calcMuscleHeatmap(
        trainingLog: List<TrainingSession>,
        days: Int = 7,
        today: java.time.LocalDate = java.time.LocalDate.parse(getCurrentDate())
    ): Map<String, HeatmapEntry> = directHeatmap(MuscleVolumeSummary.calculateWindow(
        trainingLog, today.minusDays((days.coerceAtLeast(1) - 1).toLong()), today
    ))

    fun directHeatmap(rows: List<MuscleVolumeRow>): Map<String, HeatmapEntry> =
        MuscleVolumeSummary.directCounts(rows).mapValues { (_, count) ->
            val band = DirectSetBand.of(count)
            HeatmapEntry(
                volume = count,
                intensity = band.intensity,
                level = band.name.lowercase(),
                colorHex = if (count > 0) "#7192D3" else "#38423E"
            )
        }
}
