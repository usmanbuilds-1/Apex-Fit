package com.apexfit.app.utils

import com.apexfit.app.data.*

object PlateauEngine {
    // ── PLATEAU DETECTION ─────────────────────────────────────
    fun detectPlateau(weightLog: List<WeightEntry>, nutritionLog: List<NutritionEntry>, trainingLog: List<TrainingSession>, windowDays: Int = 10): PlateauResult {
        val trend = AlgorithmEngine.calcTrendWeight(weightLog)
        if (trend.size < windowDays) return PlateauResult(false)
        val recent = trend.takeLast(windowDays)
        val change = Math.abs(recent.last().trend - recent.first().trend)
        val cutoff = getDateDaysAgo(windowDays)
        val recentLogs = nutritionLog.filter { it.date >= cutoff }
        val loggingConsistency = recentLogs.map { it.date }.distinct().size.toDouble() / windowDays
        if (change >= 0.3) return PlateauResult(false)
        if (loggingConsistency < 0.7) return PlateauResult(false)

        val volumeRecent = trainingLog.filter { it.completed && it.date >= cutoff }
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { !it.isWarmup }
            .sumOf { it.weight * it.reps }

        val earlierCutoff = getDateDaysAgo(windowDays * 2)
        val volumeEarlier = trainingLog
            .filter { it.completed && it.date >= earlierCutoff && it.date < cutoff }
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { !it.isWarmup }
            .sumOf { it.weight * it.reps }

        if (volumeEarlier == 0.0) return PlateauResult(false)
        if (volumeRecent < volumeEarlier * 0.95) return PlateauResult(false)

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
            daysStalled <= 14 -> "Reduce calories by 100-150 kcal/day for 1 week"
            daysStalled <= 21 -> "Try a 1-day refeed at maintenance calories"
            else              -> "Add one Zone 2 cardio session (NEAT increase)"
        }
        return PlateauResult(
            isPlateaued = true,
            severity = if (windowDays >= 14) "confirmed" else "early",
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
        val recent6 = trainingLog.filter { it.completed }.sortedByDescending { it.date }.take(6)
        val avgFeel = recent6.map { it.sessionFeel }.average().takeIf { !it.isNaN() } ?: 3.0
        var signals = 0
        if ((fatigue.ratio ?: 0.0) >= 1.3) signals++
        if (avgFeel <= 2.5) signals++
        if (complianceScore < 60) signals++
        if ((fatigue.ratio ?: 0.0) >= 1.5) signals += 2
        return when {
            signals >= 3 || (fatigue.ratio ?: 0.0) >= 1.5 -> DeloadResult("deload_now", "immediate", signals, listOf("Keep same exercises", "Reduce sets by half", "Drop weight 20%", "No sets to failure"))
            signals == 2 -> DeloadResult("deload_soon", "this_week", signals, listOf("Reduce to 2-3 sets per exercise", "Stay well within RIR"))
            else -> DeloadResult("continue", "none", signals)
        }
    }
}
