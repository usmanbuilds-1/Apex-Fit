package com.apexfit.app.utils

import com.apexfit.app.data.*
import kotlin.math.roundToInt

object TdeeEngine {
    // ── ADAPTIVE TDEE ─────────────────────────────────────────
    // Science: Hall (2012) - Adaptive metabolic model of energy homeostasis.
    // Daily TDEE = Avg Intake - (Weight change rate * 7700 kcal/kg).
    // Fallback: Mifflin-St Jeor BMR equation (Mifflin MD, et al. 1990) scaled by dynamic activity multiplier.
    fun calcAdaptiveTDEE(
        weightLog: List<WeightEntry>,
        nutritionLog: List<NutritionEntry>,
        windowDays: Int = 14,
        heightCm: Double = com.apexfit.app.UserDefaults.HEIGHT_CM,
        ageYears: Int = com.apexfit.app.UserDefaults.AGE_YEARS,
        biologicalSex: String = "male",
        weeklyWorkouts: Int = 4
    ): TDEEResult {
        val latestWeight = weightLog.maxByOrNull { it.date }?.weight ?: com.apexfit.app.UserDefaults.WEIGHT_KG
        
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

        if (weightLog.size < 7 || nutritionLog.map { it.date }.distinct().size < 7) {
            return TDEEResult(fallbackTdee, "low (Mifflin-St Jeor)", 0, 0.0)
        }

        val cutoff = getDateDaysAgo(windowDays)
        val recentWeight = weightLog.filter { it.date >= cutoff }.sortedBy { it.date }
        val recentNutrition = nutritionLog.filter { it.date >= cutoff }

        if (recentWeight.size < 2 || recentNutrition.size < 3) {
            return TDEEResult(fallbackTdee, "low (Mifflin-St Jeor)", recentNutrition.groupBy { it.date }
                .map { (_, e) -> e.sumOf { it.calories } }
                .average().takeIf { !it.isNaN() }?.roundToInt() ?: 0, 0.0)
        }

        val dailyCalories = recentNutrition
            .groupBy { it.date }
            .map { (_, e) -> e.sumOf { it.calories } }
        val avgCalories = if (dailyCalories.isEmpty()) 0.0 else dailyCalories.average()
        val fullTrendData = AlgorithmEngine.calcTrendWeight(weightLog.sortedBy { it.date })
        val trendAtCutoff = fullTrendData.firstOrNull { it.date >= cutoff }?.trend
            ?: fullTrendData.first().trend
        val weightChangeKg = fullTrendData.last().trend - trendAtCutoff
        
        // CRITICAL SCIENTIFIC FIX: Divisor must be actual calendar days between weights, not the number of logs
        val oldestEntry = recentWeight.first()
        val newestEntry = recentWeight.last()
        val daysBetween = getDaysBetween(oldestEntry.date, newestEntry.date).coerceAtLeast(1L)
        
        // Weight gain or loss translates to calorie imbalance per day (1kg = 7700 kcal energy density, Hall 2012)
        val calorieImbalancePerDay = (weightChangeKg * AppConstants.CALORIES_PER_KG_BODYFAT) / daysBetween
        val tdee = (avgCalories - calorieImbalancePerDay).roundToInt().coerceAtLeast(1200)
        
        val confidence = when {
            recentNutrition.map { it.date }.distinct().size >= 10 && recentWeight.map { it.date }.distinct().size >= 8 -> "high"
            recentNutrition.map { it.date }.distinct().size >= 7  && recentWeight.map { it.date }.distinct().size >= 5 -> "medium"
            else -> "low"
        }
        return TDEEResult(tdee, confidence, avgCalories.roundToInt(), Math.round(weightChangeKg * 100.0) / 100.0)
    }

    fun suggestCaloricTarget(
        tdee: Int,
        goal: String,
        currentWeightKg: Double,
        goalWeightKg: Double,
        heightCm: Double = 170.0,
        ageYears: Int = 35,
        sex: String = "male"
    ): Int {
        val distanceKg = Math.abs(goalWeightKg - currentWeightKg)

        val rateKgPerWeek = when (goal.lowercase()) {
            "gain muscle" -> (currentWeightKg * 0.004).coerceIn(0.15, 0.35)
            "lose fat"    -> (currentWeightKg * 0.008).coerceIn(0.3, 0.7)
            "recomposition" -> 0.0
            else          -> 0.0
        }

        val dailyAdjustment = ((rateKgPerWeek * 7700.0) / 7.0).roundToInt()

        val rawTarget = when (goal.lowercase()) {
            "gain muscle"   -> tdee + dailyAdjustment
            "lose fat"      -> tdee - dailyAdjustment
            "recomposition" -> tdee
            else            -> tdee
        }

        val sexOffset = if (sex.equals("female", ignoreCase = true)) -161.0 else 5.0
        val bmr = ((10.0 * currentWeightKg) + (6.25 * heightCm) - (5.0 * ageYears) + sexOffset).roundToInt()
        val floor = maxOf(1200, bmr)

        return rawTarget.coerceAtLeast(floor)
    }
}
