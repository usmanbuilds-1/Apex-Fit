package com.apexfit.app.utils

import com.apexfit.app.data.*
import kotlin.math.roundToInt

object AlgorithmEngine {
    @Volatile private var cachedTrendInput: List<WeightEntry>? = null
    @Volatile private var cachedTrendResult: List<TrendPoint>? = null

    // ── TREND WEIGHT ──────────────────────────────────────────
    // Science: Helms et al. (The Muscle and Strength Pyramid)
    // Filters daily water and glycogen fluctuations using a 14-day Exponential Moving Average (EMA).
    // Smoothing coefficient α = 2 / (N + 1) where N = 14 days, giving α ≈ 0.133.
    @Synchronized
    fun calcTrendWeight(log: List<WeightEntry>): List<TrendPoint> {
        if (log == cachedTrendInput) {
            return cachedTrendResult ?: emptyList()
        }
        if (log.isEmpty()) return emptyList()

        val dailyAveraged = log
            .groupBy { it.date }
            .map { (date, entries) ->
                WeightEntry(date = date, weight = entries.map { it.weight }.average())
            }
            .sortedBy { it.date }

        val result = mutableListOf<TrendPoint>()
        dailyAveraged.forEachIndexed { index, entry ->
            if (index == 0) {
                result.add(TrendPoint(entry.date, entry.weight, entry.weight))
            } else {
                val prev = result[index - 1].trend
                val trend = prev + 0.133 * (entry.weight - prev)
                result.add(TrendPoint(entry.date, entry.weight,
                    Math.round(trend * 100.0) / 100.0))
            }
        }
        cachedTrendInput = log
        cachedTrendResult = result
        return result
    }

    fun getCurrentTrendWeight(log: List<WeightEntry>): Double? {
        return calcTrendWeight(log).lastOrNull()?.trend
    }

    fun getWeightDirection(log: List<WeightEntry>, days: Int = 14): String {
        val trend = calcTrendWeight(log)
        if (trend.size < 2) return "insufficient_data"
        val recent = trend.takeLast(days)
        val change = recent.last().trend - recent.first().trend
        return when {
            change > 0.3 -> "gaining"
            change < -0.3 -> "losing"
            else -> "maintaining"
        }
    }

    // ── VOLUME LOAD ───────────────────────────────────────────
    fun calcWeeklyVolumePerMuscle(trainingLog: List<TrainingSession>, weeks: Int = 4): Map<String, List<Pair<String, Double>>> {
        val result = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        val weekGroups = trainingLog.filter { it.completed }.groupBy { getWeekKey(it.date) }
        weekGroups.entries.sortedBy { it.key }.takeLast(weeks).forEach { (week, sessions) ->
            val muscleVolume = mutableMapOf<String, Double>()
            sessions.forEach { session ->
                session.exercises.forEach { exercise ->
                    val volume = exercise.sets.filter { !it.isWarmup }.sumOf { it.weight * it.reps }
                    muscleVolume[exercise.muscleGroup] = (muscleVolume[exercise.muscleGroup] ?: 0.0) + volume
                }
            }
            muscleVolume.forEach { (muscle, volume) ->
                result.getOrPut(muscle) { mutableListOf() }.add(Pair(week, volume))
            }
        }
        return result
    }

    // ── HYPERTROPHY QUALITY SCORE ─────────────────────────────
    fun calcEffectiveSets(trainingLog: List<TrainingSession>, days: Int = 7): Map<String, Double> {
        val cutoff = getDateDaysAgo(days)
        val recent = trainingLog.filter { it.completed && it.date >= cutoff }
        val effectiveSets = mutableMapOf<String, Double>()
        recent.forEach { session ->
            session.exercises.forEach { exercise ->
                val working = exercise.sets.filter { !it.isWarmup && it.completed }
                working.forEach { set ->
                    val rpeModifier = com.apexfit.app.utils.ProgressionEngine.calculateEffectiveSetValue(set.rpe)
                    val score = 1.0 * rpeModifier
                    effectiveSets[exercise.muscleGroup] = (effectiveSets[exercise.muscleGroup] ?: 0.0) + score
                }
            }
        }
        return effectiveSets
    }

    // ── INJURY RISK SIGNALS ───────────────────────────────────
    fun detectInjuryRiskSignals(
        trainingLog: List<TrainingSession>,
        precomputedFatigue: FatigueResult? = null
    ): List<String> {
        val signals = mutableListOf<String>()
        val recent = trainingLog.filter { it.completed }.sortedByDescending { it.date }.take(6)

        val highRPEDays = recent.filter { session ->
            session.exercises.flatMap { it.sets }.any { it.rpe >= 9 }
        }.map { it.date }.distinct().count()
        
        if (highRPEDays >= 4) signals.add("High intensity clustering — heavy RPE sets performed on $highRPEDays distinct days recently")

        val fatigue = precomputedFatigue ?: FatigueEngine.calcFatigueToFitnessRatio(trainingLog)
        if ((fatigue.ratio ?: 0.0) >= 1.5) signals.add("Volume load too high relative to baseline — back off this week")

        val muscleDays = mutableMapOf<String, MutableSet<String>>()
        val cutoff = getDateDaysAgo(7)
        trainingLog.filter { it.completed && it.date >= cutoff }.forEach { session ->
            session.exercises.forEach { exercise ->
                muscleDays.getOrPut(exercise.muscleGroup) { mutableSetOf() }.add(session.date)
            }
        }
        muscleDays.entries.filter { it.value.size > 4 }.forEach { (muscle, dates) ->
            signals.add("$muscle trained ${dates.size} days this week — add rest before next session")
        }
        return signals
    }

    // ── UTILITIES ─────────────────────────────────────────────

    /**
     * Estimates single working set duration (Time Under Tension) based on targeted rep ranges
     * to avoid unrealistic static estimates (e.g., hardcoded 0.75 min across all loads).
     *
     * Reference: Helms et al. training tempo standard guidelines (controlled eccentrics).
     */
    fun estimateSetDurationMinutes(repsMin: Int, repsMax: Int): Double {
        val avgReps = (repsMin + repsMax) / 2.0
        val timePerRepSeconds = when {
            avgReps <= 5 -> 4.5    // Strength focus: slow eccentrics, high stability, pause
            avgReps <= 12 -> 3.0   // Hypertrophy focus: standard controlled eccentrics
            else -> 2.0            // Endurance focus: higher speed metabolic pump reps
        }
        return (avgReps * timePerRepSeconds) / 60.0
    }

    fun estimateSetDurationMinutes(ex: com.apexfit.app.data.PlanExercise, units: String = "kg"): Int {
        val setMinutes = estimateSetDurationMinutes(ex.repsMin, ex.repsMax)
        return (ex.sets * (setMinutes + ex.restSeconds / 60.0)).roundToInt()
    }
}
