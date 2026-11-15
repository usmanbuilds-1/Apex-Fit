package com.apexfit.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt
import com.apexfit.app.data.DetectedPattern

object PatternDetector {

    // ── MASTER PATTERN SCAN ───────────────────────────────────
    // Run this weekly alongside weekly report generation
    // Returns all detected patterns with confidence scores
    fun scanAllPatterns(
        weightLog: List<WeightEntry>,
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        proteinTarget: Double = 140.0
    ): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        if (nutritionLog.map { it.date }.distinct().size < 14) return patterns

        patterns.addAll(detectDayOfWeekPatterns(nutritionLog))
        patterns.addAll(detectNutritionPerformancePatterns(nutritionLog, trainingLog, proteinTarget))
        patterns.addAll(detectWeightNutritionPatterns(weightLog, nutritionLog))
        patterns.addAll(detectRecoveryPatterns(trainingLog))
        // Sleep pattern detection deferred to v2.0 — no UI or data source exists yet.

        return patterns.sortedByDescending { it.confidence }
    }

    // ── PATTERN 1 — DAY OF WEEK NUTRITION PATTERNS ────────────
    // Detects consistent under or over eating on specific days
    // Science: Haines et al 2007 — weekly dietary patterns are
    // highly consistent and predictable within individuals
    private fun detectDayOfWeekPatterns(nutritionLog: List<NutritionEntry>): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        val dayNames = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")

        // AUDIT FIX (BUG-V4-005): aggregate to daily totals first so meal-count
        // differences between days don't create false day-of-week patterns.
        // dailyTotals: date-string → Pair(totalCalories, totalProtein)
        val dailyTotals: Map<String, Pair<Double, Double>> = nutritionLog
            .groupBy { it.date }
            .mapValues { (_, entries) ->
                Pair(entries.sumOf { it.calories.toDouble() }, entries.sumOf { it.protein.toDouble() })
            }

        // Group daily totals by day-of-week
        val dayGroups = mutableMapOf<Int, MutableList<Pair<Double, Double>>>()
        dailyTotals.forEach { (dateStr, totals) ->
            try {
                val cal = Calendar.getInstance()
                cal.time = com.apexfit.app.utils.DateTimeUtils.parseDate(dateStr) ?: return@forEach
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                dayGroups.getOrPut(dow) { mutableListOf() }.add(totals)
            } catch (e: Exception) {
                // Ignore parsing errors safely
            }
        }

        val overallAvgCalories = dailyTotals.values.map { it.first }.average().takeIf { !it.isNaN() } ?: 0.0
        val overallAvgProtein = dailyTotals.values.map { it.second }.average().takeIf { !it.isNaN() } ?: 0.0

        dayGroups.forEach { (dow, dayTotalsList) ->
            if (dayTotalsList.size < 3) return@forEach
            val dayName = dayNames[(dow - 2 + 7) % 7]
            val dayAvgCal = dayTotalsList.map { it.first }.average()
            val dayAvgProtein = dayTotalsList.map { it.second }.average()
            val calDeviation = if (overallAvgCalories > 0.0)
                ((dayAvgCal - overallAvgCalories) / overallAvgCalories) * 100
            else 0.0
            val proteinDeviation = if (overallAvgProtein > 0.0)
                ((dayAvgProtein - overallAvgProtein) / overallAvgProtein) * 100
            else 0.0
            val confidence = minOf(1.0f, dayTotalsList.size / 8.0f)

            if (calDeviation < -15) {
                patterns.add(DetectedPattern(
                    id = "low_cal_$dayName",
                    type = "nutrition_day_pattern",
                    title = "$dayName is your low calorie day",
                    description = "You consistently eat ${Math.abs(calDeviation.roundToInt())}% fewer calories on ${dayName}s — averaging ${dayAvgCal.roundToInt()} kcal vs your usual ${overallAvgCalories.roundToInt()} kcal.",
                    confidence = confidence,
                    actionable = "Consider planning a protein-rich meal or snack in advance for ${dayName}s if you want this day closer to your usual intake.",
                    detectedAt = getCurrentDate()
                ))
            }

            if (calDeviation > 20) {
                patterns.add(DetectedPattern(
                    id = "high_cal_$dayName",
                    type = "nutrition_day_pattern",
                    title = "$dayName is a higher-calorie day",
                    description = "You log ${calDeviation.roundToInt()}% more calories on ${dayName}s — averaging ${dayAvgCal.roundToInt()} kcal versus your overall logged average.",
                    confidence = confidence,
                    actionable = "If this pattern is unintentional, compare meal timing and portions on ${dayName}s with your other logged days.",
                    detectedAt = getCurrentDate()
                ))
            }

            if (proteinDeviation < -20) {
                patterns.add(DetectedPattern(
                    id = "low_protein_$dayName",
                    type = "nutrition_day_pattern",
                    title = "Protein consistently drops on ${dayName}s",
                    description = "Your protein averages ${dayAvgProtein.roundToInt()}g on ${dayName}s versus ${overallAvgProtein.roundToInt()}g across your logged days. This is a consistency pattern, not evidence that one low-protein day determines weekly progress.",
                    confidence = confidence,
                    actionable = "If you want steadier protein intake, consider planning a protein-rich meal or snack earlier on ${dayName}s.",
                    detectedAt = getCurrentDate()
                ))
            }
        }
        return patterns
    }

    // ── PATTERN 2 — NUTRITION TO PERFORMANCE CORRELATION ──────
    // Detects whether eating well the day before predicts
    // better session feel scores
    // Observational only: this compares the user's own logs and must not be
    // phrased as proof that the previous day's protein caused session quality.
    private fun detectNutritionPerformancePatterns(
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        proteinTarget: Double
    ): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        val nutritionMap = nutritionLog
            .groupBy { it.date }
            .mapValues { (_, entries) ->
                entries.first().copy(
                    calories = entries.sumOf { it.calories },
                    protein  = entries.sumOf { it.protein },
                    carbs    = entries.sumOf { it.carbs },
                    fat      = entries.sumOf { it.fat }
                )
            }
        val pairs = mutableListOf<Pair<Double, Double>>()

        // One observation per training date. Multiple sessions on the same day share
        // the same previous-day nutrition exposure and are not independent evidence.
        val dailySessionFeel = trainingLog
            .filter { it.completed && it.sessionFeel > 0 }
            .groupBy { it.date }
            .mapValues { (_, sessions) -> sessions.map { it.sessionFeel }.average() }

        dailySessionFeel.forEach { (date, feel) ->
            val prevDate = getPreviousDate(date) ?: return@forEach
            val prevNutrition = nutritionMap[prevDate] ?: return@forEach
            val proteinPct = if (proteinTarget > 0.0) prevNutrition.protein.toDouble() / proteinTarget else 0.0
            pairs.add(Pair(proteinPct, feel))
        }

        if (pairs.size < 8) return patterns

        val highProteinSessions = pairs.filter { it.first >= 0.9 }
        val lowProteinSessions = pairs.filter { it.first < 0.75 }

        if (highProteinSessions.size >= 3 && lowProteinSessions.size >= 3) {
            val highAvgFeel = highProteinSessions.map { it.second }.average()
            val lowAvgFeel = lowProteinSessions.map { it.second }.average()

            if (highAvgFeel - lowAvgFeel >= 0.7) {
                val pctDiff = if (lowAvgFeel > 0.0)
                    ((highAvgFeel - lowAvgFeel) / lowAvgFeel * 100).roundToInt()
                else 0
                patterns.add(DetectedPattern(
                    id = "protein_performance_correlation",
                    type = "nutrition_performance",
                    title = "Session feel has been higher after higher-protein days",
                    description = "In your log, session feel averages ${String.format(java.util.Locale.US, "%.1f", highAvgFeel)}/5 after higher-protein days versus ${String.format(java.util.Locale.US, "%.1f", lowAvgFeel)}/5 after lower-protein days (${pctDiff}% difference). This is an association and does not show that protein caused the difference.",
                    confidence = minOf(1.0f, pairs.size / 20.0f),
                    actionable = "Keeping protein consistent may be useful, but also consider total calories, carbohydrate intake, sleep, and workout difficulty when interpreting this pattern.",
                    detectedAt = getCurrentDate()
                ))
            }
        }
        return patterns
    }

    // ── PATTERN 3 — WEIGHT RESPONSE TO NUTRITION ──────────────
    // Detects personal glycogen and water retention patterns
    // Science: Olsson & Saltin 1970 — each gram of glycogen
    // stored binds approximately 3-4g of water
    private fun detectWeightNutritionPatterns(
        weightLog: List<WeightEntry>,
        nutritionLog: List<NutritionEntry>
    ): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        if (weightLog.map { it.date }.distinct().size < 10 || nutritionLog.map { it.date }.distinct().size < 10) return patterns

        val nutritionMap = nutritionLog
            .groupBy { it.date }
            .mapValues { (_, entries) ->
                entries.first().copy(
                    calories = entries.sumOf { it.calories },
                    protein  = entries.sumOf { it.protein },
                    carbs    = entries.sumOf { it.carbs },
                    fat      = entries.sumOf { it.fat }
                )
            }
        val trendData = AlgorithmEngine.calcTrendWeight(weightLog)
        val trendMap = trendData.associateBy { it.date }
        val dailyCarbs: Map<String, Double> = nutritionLog
            .groupBy { it.date }
            .mapValues { (_, entries) -> entries.sumOf { it.carbs } }
        val weightChangePairs = mutableListOf<Pair<Int, Double>>()

        dailyCarbs.forEach { (date, totalCarbs) ->
            val twoDaysLater = getDateDaysFromNow(date, 2) ?: return@forEach
            val currentTrend = trendMap[date]?.trend ?: return@forEach
            val futureTrend = trendMap[twoDaysLater]?.trend ?: return@forEach
            weightChangePairs.add(Pair(totalCarbs.roundToInt(), futureTrend - currentTrend))
        }

        if (weightChangePairs.size < 8) return patterns

        val highCarbDays = weightChangePairs.filter { it.first > 350 }
        val lowCarbDays = weightChangePairs.filter { it.first < 200 }

        if (highCarbDays.size >= 3 && lowCarbDays.size >= 3) {
            val highCarbWeightImpact = highCarbDays.map { it.second }.average()
            val lowCarbWeightImpact = lowCarbDays.map { it.second }.average()

            if (highCarbWeightImpact > lowCarbWeightImpact + 0.15) {
                patterns.add(DetectedPattern(
                    id = "carb_water_retention",
                    type = "weight_nutrition",
                    title = "Higher-carb days have coincided with higher scale weight",
                    description = "In your log, trend weight changes by ${String.format(java.util.Locale.US, "%.2f", highCarbWeightImpact)} kg over the next two days after higher-carb days (350g+) versus ${String.format(java.util.Locale.US, "%.2f", lowCarbWeightImpact)} kg after lower-carb days. Glycogen and associated water can contribute to short-term scale changes, but this observational pattern does not prove the cause.",
                    confidence = 0.85f,
                    actionable = "Use multi-day weight trends rather than treating a short-term scale change after a higher-carb day as body-fat change.",
                    detectedAt = getCurrentDate()
                ))
            }
        }
        return patterns
    }

    // ── PATTERN 4 — RECOVERY PATTERNS ─────────────────────────
    // Looks for an association between workout spacing and session feel.
    // It cannot establish an "optimal" recovery interval because workout type,
    // intensity, sleep, and other confounders are not controlled.
    private fun detectRecoveryPatterns(trainingLog: List<TrainingSession>): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        val completedDays = trainingLog
            .filter { it.completed && it.sessionFeel > 0 }
            .groupBy { it.date }
            .map { (date, sessions) -> date to sessions.map { it.sessionFeel }.average() }
            .sortedBy { it.first }
        if (completedDays.size < 8) return patterns

        val recoveryPerformancePairs = mutableListOf<Pair<Int, Double>>()

        for (i in 1 until completedDays.size) {
            val prev = completedDays[i - 1]
            val current = completedDays[i]
            val daysBetween = getDaysBetween(prev.first, current.first).toInt()
            if (daysBetween in 1..5) {
                recoveryPerformancePairs.add(Pair(daysBetween, current.second))
            }
        }

        if (recoveryPerformancePairs.size < 6) return patterns

        val grouped = recoveryPerformancePairs.groupBy { it.first }.filter { it.value.size >= 2 }
        val bestRecoveryDays = grouped.maxByOrNull { it.value.map { p -> p.second }.average() }

        bestRecoveryDays?.let { (days, sessions) ->
            val avgFeel = sessions.map { it.second }.average()
            if (avgFeel >= 3.5 && sessions.size >= 3) {
                patterns.add(DetectedPattern(
                    id = "optimal_recovery_window",
                    type = "recovery",
                    title = "Session feel has been highest after $days day${if (days > 1) "s" else ""} between workouts",
                    description = "Your logged session feel averages ${String.format(java.util.Locale.US, "%.1f", avgFeel)}/5 when there are $days day${if (days > 1) "s" else ""} between workouts. This may reflect recovery, but workout type and difficulty can also explain the association.",
                    confidence = minOf(1.0f, sessions.size / 8.0f),
                    actionable = "If this pattern keeps repeating, compare similar workouts before changing your schedule around it.",
                    detectedAt = getCurrentDate()
                ))
            }
        }
        return patterns
    }
}
