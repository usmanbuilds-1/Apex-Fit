package com.apexfit.app.utils

import com.apexfit.app.data.*
import kotlin.math.roundToInt

object FatigueEngine {
    // ── RECENT-TO-BASELINE LOAD RATIO ─────────────────────────
    // This is a descriptive workload heuristic. Ratio cutoffs are not validated
    // as individual injury-risk thresholds and must not be presented that way.
    fun calcFatigueToFitness(trainingLog: List<TrainingSession>): FatigueResult =
        calcFatigueToFitnessRatio(trainingLog)

    fun calcFatigueToFitnessRatio(trainingLog: List<TrainingSession>): FatigueResult {
        val completed = trainingLog.filter { it.completed }
        val earliestCompleted = completed.minByOrNull { it.date }
        val daysOfHistory = if (earliestCompleted != null) getDaysBetween(earliestCompleted.date, getCurrentDate()) else 0L

        val acuteCutoff = getDateDaysAgo(6)
        val hasPreAcuteWorkouts = completed.any { it.date < acuteCutoff }

        val completedTrainingDays = completed.map { it.date }.distinct().size
        val isSparse = completedTrainingDays < 7 || daysOfHistory < 7 || !hasPreAcuteWorkouts

        if (isSparse) {
            return FatigueResult(
                ratio = null,
                status = "unknown",
                statusLabel = "No Data",
                recommendation = "Log more workouts to activate fatigue tracking",
                acuteLoad = 0.0,
                chronicLoad = 0.0
            )
        }

        val sessionLoads = completed.map { session ->
            val workingSets = session.exercises.flatMap { it.sets }.filter { !it.isWarmup && it.completed }
            val totalSets = workingSets.size
            val avgRPE = workingSets.map { it.rpe }.average().takeIf { !it.isNaN() } ?: 7.0
            val avgWeightKg = workingSets.map { it.weight }.average().takeIf { !it.isNaN() } ?: 60.0
            // Normalize relative to a 60 kg reference load; cap 0.25–4.0 to prevent
            // extreme values (100 kg deadlift day vs 5 kg curl day) from distorting ACWR.
            val weightFactor = (avgWeightKg / 60.0).coerceIn(0.25, 4.0)
            val feelModifier = when {
                session.sessionFeel == 0 -> 1.0
                session.sessionFeel <= 2 -> 1.4 - (session.sessionFeel - 1) * 0.2   // feel=1→1.4, feel=2→1.2
                session.sessionFeel == 3 -> 1.0
                session.sessionFeel <= 5 -> 1.0 - (session.sessionFeel - 3) * 0.2   // feel=4→0.8, feel=5→0.6
                else -> 1.0
            }
            Pair(session.date, totalSets.toDouble() * (avgRPE / 7.0) * weightFactor * feelModifier)
        }.sortedBy { it.first }

        val baselineCutoff = getDateDaysAgo(27)
        val acuteLoad = sessionLoads
            .filter { it.first >= acuteCutoff && it.first <= getCurrentDate() }
            .sumOf { it.second }
        // Decouple the recent week from its own baseline. The previous implementation
        // divided a 28-day total (including the acute week) by four, which guaranteed
        // that a spike also raised the denominator. Use the preceding 21 calendar days
        // as a descriptive weekly-average baseline instead.
        val baselineSessions = sessionLoads.filter {
            it.first >= baselineCutoff && it.first < acuteCutoff
        }
        val chronicLoad = if (baselineSessions.isNotEmpty()) {
            baselineSessions.sumOf { it.second } / 3.0
        } else {
            0.0
        }
        val ratio = if (chronicLoad > 0.0) acuteLoad / chronicLoad else null

        val status: String
        val statusLabel: String
        val recommendation: String
        when {
            ratio == null -> { status = "unknown"; statusLabel = "No Data"; recommendation = "Log more workouts to activate fatigue tracking" }
            ratio < 0.6 -> { status = "detraining"; statusLabel = "Well Below Baseline"; recommendation = "Recent load is well below your previous 3-week baseline. That can be intentional during recovery or a deload." }
            ratio < 0.8 -> { status = "low"; statusLabel = "Below Baseline"; recommendation = "Recent load is below your previous 3-week baseline. Increase only if that matches your plan and recovery." }
            ratio <= 1.3 -> { status = "optimal"; statusLabel = "Near Baseline"; recommendation = "Recent load is close to your previous 3-week baseline. Use performance and recovery feedback to guide changes." }
            ratio <= 1.5 -> { status = "high"; statusLabel = "Above Baseline"; recommendation = "Recent load is above your previous 3-week baseline. Consider holding or reducing load if recovery is worsening." }
            else -> { status = "danger"; statusLabel = "Well Above Baseline"; recommendation = "Recent load is well above your previous 3-week baseline. Consider a lighter week, especially if performance or recovery is declining." }
        }
        return FatigueResult(ratio, status, statusLabel, recommendation, acuteLoad, chronicLoad)
    }

    // ── MUSCLE HEATMAP ────────────────────────────────────────
    fun calcMuscleHeatmap(trainingLog: List<TrainingSession>, days: Int = 7): Map<String, HeatmapEntry> {
        val cutoff = getWindowStartDate(days)
        val recent = trainingLog.filter { it.completed && it.date >= cutoff }
        // The UI and accessibility layer describe this value as working sets.
        // Counting completed sets also keeps bodyweight work visible instead of
        // treating zero added load as zero training volume.
        val muscleVolume = mutableMapOf<String, Int>()
        recent.forEach { session ->
            session.exercises.forEach { exercise ->
                val canonicalMuscle = com.apexfit.app.utils.MuscleAliases.getCanonical(exercise.muscleGroup)
                val volume = exercise.sets.count { !it.isWarmup && it.completed }
                muscleVolume[canonicalMuscle] = (muscleVolume[canonicalMuscle] ?: 0) + volume
            }
        }
        val maxVolume = muscleVolume.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val allMuscles = MuscleGroups.ALL.map { com.apexfit.app.utils.MuscleAliases.getCanonical(it) }
        return allMuscles.associateWith { muscle ->
            val vol = muscleVolume[muscle] ?: 0
            val intensity = ((vol.toDouble() / maxVolume) * 100).roundToInt()
            HeatmapEntry(
                volume = vol,
                intensity = intensity,
                level = when { intensity >= 80 -> "high"; intensity >= 40 -> "medium"; intensity >= 10 -> "low"; else -> "untrained" },
                colorHex = when { intensity >= 80 -> "#E84040"; intensity >= 40 -> "#E8A020"; intensity >= 10 -> "#2EC46A"; else -> "#252535" }
            )
        }
    }
}
