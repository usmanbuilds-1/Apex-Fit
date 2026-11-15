package com.apexfit.app.utils

import com.apexfit.app.data.*

object PlateauEngine {
    // ── PLATEAU DETECTION ─────────────────────────────────────
    fun detectPlateau(
        weightLog: List<WeightEntry>,
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        goal: String,
        windowDays: Int = 10
    ): PlateauResult {
        // This detector only models a weight-loss plateau. Stable scale weight is
        // expected for maintenance/recomposition, and muscle-gain stalls need a
        // different model than "reduce calories".
        val normalizedGoal = goal.trim().lowercase()
        val expectsWeightLoss = normalizedGoal.contains("lose") ||
            normalizedGoal.contains("fat loss") ||
            normalizedGoal.contains("cut")
        if (!expectsWeightLoss) return PlateauResult(false)

        val safeWindowDays = windowDays.coerceAtLeast(2)
        val cutoff = getWindowStartDate(safeWindowDays)
        val today = getCurrentDate()
        val trend = AlgorithmEngine.calcTrendWeight(weightLog)
        val recent = trend.filter { it.date in cutoff..today }
        if (recent.size < 2) return PlateauResult(false)

        // Require the weight observations to span the requested calendar window.
        // Two weigh-ins a couple of days apart must not become a "14-day plateau"
        // just because older observations exist elsewhere in the log.
        val observedSpanDays = getDaysBetween(recent.first().date, recent.last().date)
        if (observedSpanDays < safeWindowDays - 1L) return PlateauResult(false)

        val change = Math.abs(recent.last().trend - recent.first().trend)
        val recentLogs = nutritionLog.filter { it.date in cutoff..today }
        val loggingConsistency = recentLogs.map { it.date }.distinct().size.toDouble() / safeWindowDays
        if (change >= 0.3) return PlateauResult(false)
        if (loggingConsistency < 0.7) return PlateauResult(false)

        val recentSets = trainingLog.filter { it.completed && it.date in cutoff..today }
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { !it.isWarmup && it.completed }

        // The preceding window is the same number of calendar days and does not
        // overlap the recent window.
        val earlierCutoff = getWindowStartDate(safeWindowDays * 2)
        val earlierSets = trainingLog
            .filter { it.completed && it.date >= earlierCutoff && it.date < cutoff }
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { !it.isWarmup && it.completed }

        val volumeRecent = recentSets.sumOf { it.weight.coerceAtLeast(0.0) * it.reps.coerceAtLeast(0) }
        val volumeEarlier = earlierSets.sumOf { it.weight.coerceAtLeast(0.0) * it.reps.coerceAtLeast(0) }

        // Prefer load-volume when it exists. For bodyweight-only histories where
        // external load is zero, fall back to completed working-set counts rather
        // than pretending the user did no training.
        val maintainedTraining = if (volumeEarlier > 0.0) {
            volumeRecent >= volumeEarlier * 0.95
        } else {
            earlierSets.isNotEmpty() && recentSets.size >= kotlin.math.ceil(earlierSets.size * 0.95).toInt()
        }
        if (!maintainedTraining) return PlateauResult(false)

        var daysStalled = 0
        if (trend.isNotEmpty()) {
            val currentTrend = trend.last().trend
            val lastChangeIndex = trend.indexOfLast { Math.abs(currentTrend - it.trend) > 0.1 }
            if (lastChangeIndex != -1) {
                daysStalled = getDaysBetween(trend[lastChangeIndex].date, trend.last().date).toInt()
            } else {
                daysStalled = getDaysBetween(trend.first().date, trend.last().date).toInt()
            }
        }

        val intervention = when {
            daysStalled <= 7  -> "Weigh food for 3 days to recheck portions"
            daysStalled <= 14 -> "If intake has been accurate, consider reducing the target by 100-150 kcal/day for 1 week"
            daysStalled <= 21 -> "Keep the deficit consistent and review activity, portions, and recovery before cutting further"
            else              -> "Review your calorie target and daily activity before making a larger adjustment"
        }
        return PlateauResult(
            isPlateaued = true,
            severity = if (safeWindowDays >= 14) "confirmed" else "early",
            interventions = listOf(intervention),
            daysStalled = daysStalled
        )
    }

    // ── DELOAD PLANNER ────────────────────────────────────────
    fun calcDeloadRecommendation(
        trainingLog: List<TrainingSession>,
        complianceScore: Int,
        precomputedFatigue: FatigueResult? = null
    ): DeloadResult {
        val fatigue = precomputedFatigue ?: FatigueEngine.calcFatigueToFitnessRatio(trainingLog)
        val recentRatedDays = trainingLog
            .filter { it.completed && it.sessionFeel in 1..5 }
            .groupBy { it.date }
            .map { (date, sessions) -> date to sessions.map { it.sessionFeel }.average() }
            .sortedByDescending { it.first }
            .take(6)
        val avgFeel = recentRatedDays.map { it.second }
            .average()
            .takeIf { !it.isNaN() }

        // Compliance is not a fatigue marker: training less than planned must not
        // become evidence that the user needs to train even less.
        @Suppress("UNUSED_VARIABLE")
        val ignoredComplianceScore = complianceScore

        val ratio = fatigue.ratio
        val elevatedLoad = ratio != null && ratio >= 1.3
        val veryElevatedLoad = ratio != null && ratio >= 1.5
        val persistentPoorFeel = recentRatedDays.size >= 3 && (avgFeel ?: 5.0) <= 2.5

        // The load ratio is a contextual monitoring heuristic, not a stand-alone
        // injury/deload predictor. Require corroborating recovery/performance feedback
        // before prescribing a deload from it.
        val signals = listOf(elevatedLoad, persistentPoorFeel).count { it }
        return when {
            veryElevatedLoad && persistentPoorFeel ->
                DeloadResult(
                    "deload_now",
                    "immediate",
                    signals,
                    listOf("Keep the same exercises", "Reduce sets by roughly half", "Use lighter loads", "Avoid sets to failure")
                )
            elevatedLoad && persistentPoorFeel ->
                DeloadResult(
                    "deload_soon",
                    "this_week",
                    signals,
                    listOf("Reduce training volume this week", "Keep several reps in reserve")
                )
            else -> DeloadResult("continue", "none", signals)
        }
    }
}
