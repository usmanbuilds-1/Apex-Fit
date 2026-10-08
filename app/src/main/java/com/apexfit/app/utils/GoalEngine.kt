package com.apexfit.app.utils

import com.apexfit.app.data.*
import kotlin.math.roundToInt

data class GoalTimeline(
    val weeksToGoal: Int,
    val weeklyChangeKg: Double,
    val isRealistic: Boolean,
    val adjustedGoalWeightKg: Double,
    val summaryLine: String
)

object GoalEngine {
    fun estimateLBM(weightKg: Double, heightCm: Double, sex: String): Double {
        // Boer formula (Boer, 1984)
        return if (sex.equals("female", ignoreCase = true)) {
            (0.252 * weightKg) + (0.473 * heightCm) - 48.3
        } else {
            (0.407 * weightKg) + (0.267 * heightCm) - 19.2
        }
    }

    fun calcGoalTimeline(
        currentWeightKg: Double,
        goalWeightKg: Double,
        resolvedGoal: String,
        heightCm: Double,
        sex: String
    ): GoalTimeline {
        val isRecomposition = resolvedGoal.equals("Recomposition", ignoreCase = true)
        val weeklyRate = when (resolvedGoal.lowercase()) {
            "gain muscle"   -> (currentWeightKg * 0.004).coerceIn(0.15, 0.35)
            "lose fat"      -> (currentWeightKg * 0.008).coerceIn(0.30, 0.70)
            "recomposition" -> 0.0
            else            -> 0.0
        }
        val distanceKg  = Math.abs(goalWeightKg - currentWeightKg)
        val weeksRaw    = if (weeklyRate > 0.0) 
            (distanceKg / weeklyRate).roundToInt() else 0
        val isRealistic = weeksRaw <= 104

        val direction = if (goalWeightKg >= currentWeightKg) 1.0 else -1.0
        val adjustedGoal = if (!isRealistic)
            currentWeightKg + direction * weeklyRate * 52.0
        else goalWeightKg

        val summaryLine = when {
            isRecomposition ->
                "Body recomposition: eat at maintenance, lift heavy. " +
                "Expect visual changes in 3–6 months."
            !isRealistic ->
                "At a safe rate that would take over 2 years. " +
                "First milestone: ${Math.round(adjustedGoal * 10.0) / 10.0} kg."
            weeksRaw <= 12 ->
                "~$weeksRaw weeks at ${Math.round(weeklyRate * 100.0) / 100.0} kg/week."
            else ->
                "~${(weeksRaw / 4.33).roundToInt()} months at " +
                "${Math.round(weeklyRate * 100.0) / 100.0} kg/week."
        }
        return GoalTimeline(
            weeksToGoal           = if (isRecomposition) 0 else weeksRaw.coerceAtMost(104),
            weeklyChangeKg        = if (isRecomposition) 0.0 else weeklyRate,
            isRealistic           = isRealistic,
            adjustedGoalWeightKg  = adjustedGoal,
            summaryLine           = summaryLine
        )
    }
}
