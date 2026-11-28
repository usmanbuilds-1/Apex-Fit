package com.apexfit.app.utils

import com.apexfit.app.data.*
import kotlin.math.roundToInt
import kotlin.math.ceil

data class GoalTimeline(
    val weeksToGoal: Int,
    val weeklyChangeKg: Double,
    val isRealistic: Boolean,
    val adjustedGoalWeightKg: Double,
    val summaryLine: String
)

object GoalEngine {
    // BMI is a screening threshold, not a diagnosis or body-composition measure.
    // Do not automatically prescribe a deficit for underweight adults or a goal
    // that would take an adult below the conventional healthy-weight range.
    fun blocksAutomaticWeightLoss(currentWeightKg: Double, goalWeightKg: Double?, heightCm: Double): Boolean {
        if (!heightCm.isFinite() || heightCm <= 0 || !currentWeightKg.isFinite() || currentWeightKg <= 0)
            return true
        val heightM = heightCm / 100.0
        val minimum = 18.5 * heightM * heightM
        return currentWeightKg <= minimum || (goalWeightKg != null &&
            (!goalWeightKg.isFinite() || goalWeightKg < minimum))
    }

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
        val normalizedGoal = resolvedGoal.trim().lowercase().let { if (it == "build muscle") "gain muscle" else it }
        if (normalizedGoal == "lose fat" && blocksAutomaticWeightLoss(currentWeightKg, goalWeightKg, heightCm)) {
            return GoalTimeline(0, 0.0, false, currentWeightKg,
                "Automatic weight-loss guidance is unavailable for a low current or goal weight. Review your goal with a qualified clinician.")
        }
        val isRecomposition = normalizedGoal == "recomposition"
        val isMaintain = normalizedGoal.contains("maintain")
        val contradictoryOrReached = when (normalizedGoal) {
            "gain muscle" -> goalWeightKg <= currentWeightKg
            "lose fat" -> goalWeightKg >= currentWeightKg
            else -> false
        }
        if (contradictoryOrReached) {
            return GoalTimeline(
                weeksToGoal = 0,
                weeklyChangeKg = 0.0,
                isRealistic = true,
                adjustedGoalWeightKg = currentWeightKg,
                summaryLine = "Your target weight is already reached or conflicts with the selected goal. Update the target before using a gain/loss timeline."
            )
        }

        val weeklyRate = when (normalizedGoal) {
            "gain muscle"   -> (currentWeightKg * 0.004).coerceIn(0.15, 0.35)
            "lose fat"      -> (currentWeightKg * 0.008).coerceIn(0.30, 0.70)
            "recomposition" -> 0.0
            else            -> 0.0
        }
        val distanceKg  = Math.abs(goalWeightKg - currentWeightKg)
        // Round up so the timeline never promises reaching the target sooner
        // than the selected weekly rate can mathematically achieve.
        val weeksRaw    = if (weeklyRate > 0.0)
            ceil(distanceKg / weeklyRate).toInt() else 0
        val isRealistic = weeksRaw <= 104

        val direction = if (goalWeightKg >= currentWeightKg) 1.0 else -1.0
        val adjustedGoal = if (!isRealistic)
            currentWeightKg + direction * weeklyRate * 52.0
        else goalWeightKg

        val summaryLine = when {
            isRecomposition ->
                "Body recomposition: stay near maintenance and use progressive resistance training. " +
                "Judge progress from multi-week trends rather than a fixed visual-change deadline."
            isMaintain ->
                "Maintain your current weight range while tracking trend, nutrition, and training consistency."
            !isRealistic ->
                "At the current target rate this would take over 2 years. " +
                "First milestone: ${Math.round(adjustedGoal * 10.0) / 10.0} kg."
            weeksRaw <= 12 ->
                "~$weeksRaw weeks at ${Math.round(weeklyRate * 100.0) / 100.0} kg/week."
            else ->
                "~${(weeksRaw / 4.33).roundToInt()} months at " +
                "${Math.round(weeklyRate * 100.0) / 100.0} kg/week."
        }
        return GoalTimeline(
            weeksToGoal           = if (isRecomposition || isMaintain) 0 else weeksRaw.coerceAtMost(104),
            weeklyChangeKg        = if (isRecomposition || isMaintain) 0.0 else weeklyRate,
            isRealistic           = isRealistic,
            adjustedGoalWeightKg  = adjustedGoal,
            summaryLine           = summaryLine
        )
    }
}
