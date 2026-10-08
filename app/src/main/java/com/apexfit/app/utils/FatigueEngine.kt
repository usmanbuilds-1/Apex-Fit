package com.apexfit.app.utils

import com.apexfit.app.data.*
import kotlin.math.roundToInt

object FatigueEngine {
    // ── FATIGUE TO FITNESS RATIO ──────────────────────────────
    fun calcFatigueToFitness(trainingLog: List<TrainingSession>): FatigueResult =
        calcFatigueToFitnessRatio(trainingLog)

    fun calcFatigueToFitnessRatio(trainingLog: List<TrainingSession>): FatigueResult {
        val completed = trainingLog.filter { it.completed }
        val earliestCompleted = completed.minByOrNull { it.date }
        val daysOfHistory = if (earliestCompleted != null) getDaysBetween(earliestCompleted.date, getCurrentDate()) else 0L

        val acuteCutoff = getDateDaysAgo(6)
        val hasPreAcuteWorkouts = completed.any { it.date < acuteCutoff }

        val isSparse = completed.size < 7 || daysOfHistory < 7 || !hasPreAcuteWorkouts

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

        val chronicCutoff = getDateDaysAgo(27)
        val acuteLoad = sessionLoads.filter { it.first >= acuteCutoff }.sumOf { it.second }
        val chronicSessions = sessionLoads.filter { it.first >= chronicCutoff }
        val chronicLoad = if (chronicSessions.isNotEmpty()) chronicSessions.sumOf { it.second } / 4.0 else acuteLoad
        val ratio = if (chronicLoad > 0) acuteLoad / chronicLoad else null

        val status: String
        val statusLabel: String
        val recommendation: String
        when {
            ratio == null -> { status = "unknown"; statusLabel = "No Data"; recommendation = "Log more workouts to activate fatigue tracking" }
            ratio < 0.6 -> { status = "detraining"; statusLabel = "Low Load"; recommendation = "Load is below your usual baseline — appropriate for a deload or recovery week" }
            ratio < 0.8 -> { status = "low"; statusLabel = "Below optimal"; recommendation = "Slightly increase volume or intensity" }
            ratio <= 1.3 -> { status = "optimal"; statusLabel = "Optimal Load"; recommendation = "Training load is in a productive range — maintain this" }
            ratio <= 1.5 -> { status = "high"; statusLabel = "Elevated Load"; recommendation = "Load is running high — consider backing off 10-15% this week" }
            else -> { status = "danger"; statusLabel = "High Load Warning"; recommendation = "Load is significantly above baseline — consider reducing volume or taking a lighter week" }
        }
        return FatigueResult(ratio, status, statusLabel, recommendation, acuteLoad, chronicLoad)
    }

    // ── MUSCLE HEATMAP ────────────────────────────────────────
    fun calcMuscleHeatmap(trainingLog: List<TrainingSession>, days: Int = 7): Map<String, HeatmapEntry> {
        val cutoff = getDateDaysAgo(days)
        val recent = trainingLog.filter { it.completed && it.date >= cutoff }
        val muscleVolume = mutableMapOf<String, Double>()
        recent.forEach { session ->
            session.exercises.forEach { exercise ->
                val canonicalMuscle = com.apexfit.app.utils.MuscleAliases.getCanonical(exercise.muscleGroup)
                val volume = exercise.sets.filter { !it.isWarmup }.sumOf { it.weight * it.reps }
                muscleVolume[canonicalMuscle] = (muscleVolume[canonicalMuscle] ?: 0.0) + volume
            }
        }
        val maxVolume = muscleVolume.values.maxOrNull() ?: 1.0
        val allMuscles = MuscleGroups.ALL.map { com.apexfit.app.utils.MuscleAliases.getCanonical(it) }
        return allMuscles.associateWith { muscle ->
            val vol = muscleVolume[muscle] ?: 0.0
            val intensity = ((vol / maxVolume) * 100).roundToInt()
            HeatmapEntry(
                volume = vol.roundToInt(),
                intensity = intensity,
                level = when { intensity >= 80 -> "high"; intensity >= 40 -> "medium"; intensity >= 10 -> "low"; else -> "untrained" },
                colorHex = when { intensity >= 80 -> "#E84040"; intensity >= 40 -> "#E8A020"; intensity >= 10 -> "#2EC46A"; else -> "#252535" }
            )
        }
    }
}
