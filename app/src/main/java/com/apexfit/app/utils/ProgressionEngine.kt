package com.apexfit.app.utils

import kotlin.math.roundToInt

object ProgressionEngine {

    /**
     * Calculates 1RM with strict boundary clamping for r = 1 and Brzycki high-rep stability.
     */
    fun calculateOneRepMax(weight: Double, reps: Int, rpe: Double? = null): Double {
        if (weight <= 0.0 || reps <= 0) return 0.0

        val effectiveReps: Double = if (rpe != null && rpe in 6.0..10.0) {
            val rir = 10.0 - rpe
            reps.toDouble() + rir
        } else {
            reps.toDouble()
        }

        // Fix: Exactly 1 rep must equal the lifted weight (eliminating Epley 1.033x inflation)
        if (effectiveReps <= 1.0) return weight

        val calculatedMax = if (effectiveReps <= 10.0) {
            weight * (1.0 + (effectiveReps / 30.0))
        } else {
            val clampedReps = effectiveReps.coerceAtMost(30.0)
            weight * (36.0 / (37.0 - clampedReps))
        }

        return (calculatedMax * 100.0).roundToInt() / 100.0
    }

    fun evaluateProgression(
        currentWeight: Double,
        targetMinReps: Int,
        targetMaxReps: Int,
        completedRepsPerSet: List<Int>,
        isUpperBody: Boolean = true
    ): ProgressionDecision {
        if (completedRepsPerSet.isEmpty()) {
            return ProgressionDecision(
                action = Action.MAINTAIN,
                nextWeight = currentWeight,
                summary = "No completed sets recorded."
            )
        }

        val allHitMax = completedRepsPerSet.all { it >= targetMaxReps }
        val failedMinThreshold = completedRepsPerSet.count { it < targetMinReps }

        return when {
            allHitMax -> {
                val step = if (isUpperBody) 2.5 else 5.0
                ProgressionDecision(
                    action = Action.INCREASE,
                    nextWeight = currentWeight + step,
                    summary = "Target achieved across all sets ($targetMaxReps reps). Incrementing load by $step."
                )
            }
            failedMinThreshold >= (completedRepsPerSet.size / 2.0).roundToInt() -> {
                ProgressionDecision(
                    action = Action.DELOAD,
                    nextWeight = (currentWeight * 0.9).roundToInt().toDouble(),
                    summary = "Multiple sets dropped below minimum threshold ($targetMinReps reps). Suggested: 10% deload."
                )
            }
            else -> {
                ProgressionDecision(
                    action = Action.MAINTAIN,
                    nextWeight = currentWeight,
                    summary = "Working within rep zone ($targetMinReps-$targetMaxReps). Continue building volume."
                )
            }
        }
    }

    enum class Action { INCREASE, MAINTAIN, DELOAD }

    data class ProgressionDecision(
        val action: Action,
        val nextWeight: Double,
        val summary: String
    )
}