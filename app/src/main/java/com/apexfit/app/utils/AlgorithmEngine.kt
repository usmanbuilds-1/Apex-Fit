package com.apexfit.app.utils

import com.apexfit.app.data.*
import kotlin.math.roundToInt

object AlgorithmEngine {
    @Volatile private var cachedTrendInput: List<WeightEntry>? = null
    @Volatile private var cachedTrendResult: List<TrendPoint>? = null

    // ── TREND WEIGHT ──────────────────────────────────────────
    // Science: Helms et al. (The Muscle and Strength Pyramid)
    // Filters daily water and glycogen fluctuations using a 14-day Exponential Moving Average (EMA).
    // Daily smoothing coefficient α = 2 / (N + 1) where N = 14; gaps are advanced with the equivalent calendar-time decay.
    @Synchronized
    fun calcTrendWeight(log: List<WeightEntry>): List<TrendPoint> {
        if (log == cachedTrendInput) {
            return cachedTrendResult ?: emptyList()
        }
        if (log.isEmpty()) return emptyList()

        val dailyAveraged = BodyWeightHistory.daily(log)
        if (dailyAveraged.isEmpty()) return emptyList()

        val result = mutableListOf<TrendPoint>()
        val dailyAlpha = 2.0 / 15.0
        var rawTrend = dailyAveraged.first().weight
        dailyAveraged.forEachIndexed { index, entry ->
            if (index == 0) {
                result.add(TrendPoint(entry.date, entry.weight, rawTrend))
            } else {
                val previousDate = dailyAveraged[index - 1].date
                val gapDays = getDaysBetween(previousDate, entry.date).coerceAtLeast(1L)
                // Advance the daily EMA across the actual calendar gap. This keeps
                // "14-day EMA" semantics when weigh-ins are sparse instead of treating
                // five calendar days without a weigh-in as a single day.
                val effectiveAlpha = 1.0 - Math.pow(1.0 - dailyAlpha, gapDays.toDouble())
                rawTrend += effectiveAlpha * (entry.weight - rawTrend)
                result.add(
                    TrendPoint(
                        entry.date,
                        entry.weight,
                        Math.round(rawTrend * 100.0) / 100.0
                    )
                )
            }
        }
        cachedTrendInput = log
        cachedTrendResult = result
        return result
    }

    fun getCurrentTrendWeight(log: List<WeightEntry>): Double? {
        return calcTrendWeight(log.filter { it.date <= getCurrentDate() }).lastOrNull()?.trend
    }

    fun getWeightDirection(log: List<WeightEntry>, days: Int = 14): String {
        val today = java.time.LocalDate.parse(getCurrentDate())
        val cutoff = today.minusDays((days.coerceAtLeast(1) - 1).toLong())
        val trend = calcTrendWeight(log.filter { it.date <= today.toString() })
        val recent = trend.filter { it.date >= cutoff.toString() }
        // Do not classify from two isolated measurements or stale observations.
        if (recent.size < 4) return "insufficient_data"
        val span = getDaysBetween(recent.first().date, recent.last().date)
        if (span < 7 || getDaysBetween(recent.last().date, today.toString()) > 3) return "insufficient_data"
        val first = recent.first().trend
        val changePerWeekPct = (recent.last().trend - first) / first * 100.0 * 7.0 / span
        // A descriptive 0.25% body-weight/week deadband, not a statistical
        // confidence interval or a direct measure of fat or muscle change.
        return when {
            changePerWeekPct > 0.25 -> "gaining"
            changePerWeekPct < -0.25 -> "losing"
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
                    val volume = exercise.sets
                        .filter { !it.isWarmup && it.completed }
                        .sumOf { it.weight * it.reps }
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
        val cutoff = getWindowStartDate(days)
        val recent = TrainingEvidence.sessions(trainingLog, java.time.LocalDate.parse(cutoff), java.time.LocalDate.parse(getCurrentDate()))
        val seenIds = mutableSetOf<Long>()
        val effectiveSets = mutableMapOf<String, Double>()
        recent.forEach { session ->
            session.exercises.forEach { exercise ->
                val working = exercise.sets.filter { TrainingEvidence.isWorkingSet(it) && it.rpe in 1..10 && (it.id == 0L || seenIds.add(it.id)) }
                working.forEach { set ->
                    val rpeModifier = com.apexfit.app.utils.ProgressionEngine.calculateEffectiveSetValue(set.rpe)
                    val score = 1.0 * rpeModifier
                    effectiveSets[exercise.muscleGroup] = (effectiveSets[exercise.muscleGroup] ?: 0.0) + score
                }
            }
        }
        return effectiveSets
    }

    // ── OBSERVED TRAINING PATTERNS ────────────────────────────
    // These are descriptive recent-session patterns, not injury predictions.
    // Workload ratios stay in the separate fatigue view and never create these signals.
    fun detectTrainingPatternSignals(trainingLog: List<TrainingSession>): List<String> {
        val signals = mutableListOf<String>()
        val recent = trainingLog.filter { it.completed }.sortedByDescending { it.date }.take(6)

        val highRPEDays = recent.filter { session ->
            session.exercises.flatMap { it.sets }.any { it.completed && !it.isWarmup && it.rpe >= 9 }
        }.map { it.date }.distinct().count()
        
        if (highRPEDays >= 4) signals.add("High intensity clustering — heavy RPE sets performed on $highRPEDays distinct days recently")

        val muscleDays = mutableMapOf<String, MutableSet<String>>()
        val cutoff = getWindowStartDate(7)
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
        val workingSets = ex.sets.coerceAtLeast(0)
        val restIntervals = (workingSets - 1).coerceAtLeast(0)
        val totalMinutes = workingSets * setMinutes + restIntervals * (ex.restSeconds.coerceAtLeast(0) / 60.0)
        return totalMinutes.roundToInt()
    }
}
