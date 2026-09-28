package com.apexfit.app.utils

import kotlin.math.exp
import kotlin.math.max

object ReadinessEngine {

    private val BASELINE_RECOVERY_HOURS = mapOf(
        "Chest" to 48.0,
        "Back" to 48.0,
        "Quadriceps" to 72.0,
        "Hamstrings" to 72.0,
        "Glutes" to 48.0,
        "Shoulders" to 36.0,
        "Biceps" to 36.0,
        "Triceps" to 36.0,
        "Calves" to 24.0,
        "Abs" to 24.0
    )

    fun calculateMuscleReadiness(
        muscleGroup: String,
        totalVolumeKg: Double,
        hoursElapsed: Double,
        averageRpe: Double = 8.0
    ): Double {
        if (hoursElapsed < 0.0) return 0.0
        val baseHours = BASELINE_RECOVERY_HOURS[muscleGroup] ?: 48.0

        val rpeFactor = 1.0 + max(0.0, (averageRpe - 7.0) * 0.15)
        val adjustedRecoveryTarget = baseHours * rpeFactor

        if (hoursElapsed >= adjustedRecoveryTarget) {
            return 100.0
        }

        // Sigmoidal recovery curve
        val progressRatio = hoursElapsed / adjustedRecoveryTarget
        val sigmoidScore = (1.0 / (1.0 + exp(-6.0 * (progressRatio - 0.5)))) * 100.0

        return sigmoidScore.coerceIn(5.0, 100.0)
    }

    fun getReadinessState(readinessPercent: Double): ReadinessState {
        return when {
            readinessPercent >= 85.0 -> ReadinessState.OPTIMAL
            readinessPercent >= 60.0 -> ReadinessState.RECOVERED_ENOUGH
            readinessPercent >= 35.0 -> ReadinessState.FATIGUED
            else -> ReadinessState.EXHAUSTED
        }
    }

    enum class ReadinessState(val label: String, val hexColor: Long) {
        OPTIMAL("Optimal", 0xFF00E676),
        RECOVERED_ENOUGH("Ready", 0xFF69F0AE),
        FATIGUED("Fatigued", 0xFFFFD740),
        EXHAUSTED("Needs Rest", 0xFFFF2A4B)
    }
}