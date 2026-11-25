package com.apexfit.app.utils

import com.apexfit.app.data.*
import kotlin.math.roundToInt

object TdeeEngine {
    // ── ADAPTIVE TDEE ─────────────────────────────────────────
    // Energy-balance estimate from logged intake and scale-weight trend.
    // The 7,700 kcal/kg conversion is a practical approximation, not a dynamic
    // metabolic model and should not be presented as one.
    // Fallback: Mifflin-St Jeor BMR equation (Mifflin MD, et al. 1990) scaled by an activity multiplier.
    fun calcAdaptiveTDEE(
        weightLog: List<WeightEntry>,
        nutritionLog: List<NutritionEntry>,
        windowDays: Int = 14,
        heightCm: Double = com.apexfit.app.UserDefaults.HEIGHT_CM,
        ageYears: Int = com.apexfit.app.UserDefaults.AGE_YEARS,
        biologicalSex: String = "male",
        weeklyWorkouts: Int = 4,
        fallbackWeightKg: Double = com.apexfit.app.UserDefaults.WEIGHT_KG
    ): TDEEResult {
        val latestWeight = BodyWeightHistory.latest(weightLog)
            ?.weight
            ?: fallbackWeightKg
        
        // Mifflin-St Jeor Equation (Mifflin MD, et al. 1990)
        // Males: REE = (10 * weight_kg) + (6.25 * height_cm) - (5 * age_years) + 5
        // Females: REE = (10 * weight_kg) + (6.25 * height_cm) - (5 * age_years) - 161
        val sexOffset = if (biologicalSex.equals("female", ignoreCase = true)) -161.0 else 5.0
        val bmrBaseline = (10.0 * latestWeight) + (6.25 * heightCm) - (5.0 * ageYears) + sexOffset
        
        // Dynamic Activity Multiplier based on estimated/target frequency:
        // Sedentary (1.2): 0-1 workouts / week
        // Lightly active (1.375): 2-3 workouts / week
        // Moderately active (1.55): 4-5 workouts / week
        // Very active (1.725): 6+ workouts / week
        val activityMultiplier = when {
            weeklyWorkouts <= 1 -> 1.2
            weeklyWorkouts <= 3 -> 1.375
            weeklyWorkouts <= 5 -> 1.55
            else -> 1.725
        }
        val fallbackTdee = (bmrBaseline * activityMultiplier).roundToInt()

        val cutoff = getWindowStartDate(windowDays)
        val today = getCurrentDate()
        val recentWeight = weightLog
            .filter { it.date in cutoff..today && BodyWeightHistory.valid(it) }
            .sortedWith(BodyWeightHistory.chronological)
        val recentNutrition = nutritionLog.filter { it.date in cutoff..today }
        val recentWeightDays = recentWeight.map { it.date }.distinct().size
        val recentNutritionDays = recentNutrition.map { it.date }.distinct().size

        // Adaptive estimates need recent evidence, not merely enough historical
        // rows somewhere in the database. Old data outside the active window must
        // not unlock an "adaptive" calorie estimate from only a couple of current
        // weigh-ins or nutrition days.
        if (recentWeightDays < 7 || recentNutritionDays < 7) {
            return TDEEResult(
                fallbackTdee,
                "low (Mifflin-St Jeor; insufficient recent data)",
                recentNutrition.groupBy { it.date }
                .map { (_, e) -> e.sumOf { it.calories } }
                .average().takeIf { !it.isNaN() }?.roundToInt() ?: 0,
                0.0
            )
        }

        // Adaptive energy-balance math only makes sense when intake coverage spans
        // the same calendar interval as the weight change. Do not silently average
        // a handful of selectively logged days and treat them as the whole interval.
        val oldestEntry = recentWeight.first()
        val newestEntry = recentWeight.last()
        val daysBetween = getDaysBetween(oldestEntry.date, newestEntry.date).coerceAtLeast(1L)
        val calendarDays = (daysBetween + 1L).toInt()
        val nutritionInWeightWindow = recentNutrition.filter {
            it.date >= oldestEntry.date && it.date <= newestEntry.date
        }
        val loggedNutritionDays = nutritionInWeightWindow.map { it.date }.distinct().size
        val nutritionCoverage = loggedNutritionDays.toDouble() / calendarDays.coerceAtLeast(1)

        if (nutritionCoverage < 0.80) {
            val loggedDailyCalories = nutritionInWeightWindow
                .groupBy { it.date }
                .map { (_, e) -> e.sumOf { it.calories } }
            val loggedAverage = loggedDailyCalories.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
            return TDEEResult(
                fallbackTdee,
                "low (Mifflin-St Jeor; sparse nutrition coverage)",
                loggedAverage,
                0.0
            )
        }

        val dailyCalories = nutritionInWeightWindow
            .groupBy { it.date }
            .map { (_, e) -> e.sumOf { it.calories } }
        val avgCalories = if (dailyCalories.isEmpty()) 0.0 else dailyCalories.average()
        val fullTrendData = AlgorithmEngine.calcTrendWeight(weightLog.sortedBy { it.date })
        val trendAtCutoff = fullTrendData.firstOrNull { it.date >= oldestEntry.date }?.trend
            ?: fullTrendData.first().trend
        val weightChangeKg = fullTrendData.last().trend - trendAtCutoff
        
        // Divisor is actual calendar days between the weight observations, not log count.
        
        // Approximate observed energy imbalance from scale-weight change using 7,700 kcal/kg.
        val calorieImbalancePerDay = (weightChangeKg * AppConstants.CALORIES_PER_KG_BODYFAT) / daysBetween
        val tdee = (avgCalories - calorieImbalancePerDay).roundToInt().coerceAtLeast(1200)
        
        val confidence = when {
            loggedNutritionDays >= 10 && recentWeight.map { it.date }.distinct().size >= 8 -> "high"
            loggedNutritionDays >= 7  && recentWeight.map { it.date }.distinct().size >= 5 -> "medium"
            else -> "low"
        }
        return TDEEResult(tdee, confidence, avgCalories.roundToInt(), Math.round(weightChangeKg * 100.0) / 100.0)
    }

    fun suggestCaloricTarget(
        tdee: Int,
        goal: String,
        currentWeightKg: Double,
        goalWeightKg: Double?,
        heightCm: Double = 170.0,
        ageYears: Int = 35,
        sex: String = "male"
    ): Int {
        val normalizedGoal = goal.trim().lowercase()
        val targetAlreadyReachedOrContradictory = when {
            goalWeightKg == null -> false
            normalizedGoal == "gain muscle" -> goalWeightKg <= currentWeightKg
            normalizedGoal == "lose fat" -> goalWeightKg >= currentWeightKg
            else -> false
        }

        val rateKgPerWeek = if (targetAlreadyReachedOrContradictory) {
            0.0
        } else {
            when (normalizedGoal) {
                "gain muscle" -> (currentWeightKg * 0.004).coerceIn(0.15, 0.35)
                "lose fat"    -> (currentWeightKg * 0.008).coerceIn(0.3, 0.7)
                "recomposition" -> 0.0
                else          -> 0.0
            }
        }

        val dailyAdjustment = ((rateKgPerWeek * 7700.0) / 7.0).roundToInt()

        val rawTarget = when {
            targetAlreadyReachedOrContradictory -> tdee
            normalizedGoal == "gain muscle" -> tdee + dailyAdjustment
            normalizedGoal == "lose fat" -> tdee - dailyAdjustment
            else -> tdee
        }

        val sexOffset = if (sex.equals("female", ignoreCase = true)) -161.0 else 5.0
        val bmr = ((10.0 * currentWeightKg) + (6.25 * heightCm) - (5.0 * ageYears) + sexOffset).roundToInt()
        val floor = maxOf(1200, bmr)

        return rawTarget.coerceAtLeast(floor)
    }
}
