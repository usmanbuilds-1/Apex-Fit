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
        if (nutritionLog.size < 14) return patterns

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
        val dayGroups = mutableMapOf<Int, MutableList<NutritionEntry>>()

        nutritionLog.forEach { entry ->
            try {
                val cal = Calendar.getInstance()
                cal.time = com.apexfit.app.utils.DateTimeUtils.parseDate(entry.date) ?: return@forEach
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                dayGroups.getOrPut(dow) { mutableListOf() }.add(entry)
            } catch (e: Exception) {
                // Ignore parsing errors safely
            }
        }

        val overallAvgCalories = nutritionLog.map { it.calories }.average()
        val overallAvgProtein = nutritionLog.map { it.protein }.average()

        dayGroups.forEach { (dow, entries) ->
            if (entries.size < 3) return@forEach
            val dayName = dayNames[(dow - 2 + 7) % 7]
            val dayAvgCal = entries.map { it.calories }.average()
            val dayAvgProtein = entries.map { it.protein }.average()
            val calDeviation = if (overallAvgCalories > 0.0)
                ((dayAvgCal - overallAvgCalories) / overallAvgCalories) * 100
            else 0.0
            val proteinDeviation = if (overallAvgProtein > 0.0)
                ((dayAvgProtein - overallAvgProtein) / overallAvgProtein) * 100
            else 0.0
            val confidence = minOf(1.0f, entries.size / 8.0f)

            if (calDeviation < -15) {
                patterns.add(DetectedPattern(
                    id = "low_cal_$dayName",
                    type = "nutrition_day_pattern",
                    title = "$dayName is your low calorie day",
                    description = "You consistently eat ${Math.abs(calDeviation.roundToInt())}% fewer calories on ${dayName}s — averaging ${dayAvgCal.roundToInt()} kcal vs your usual ${overallAvgCalories.roundToInt()} kcal.",
                    confidence = confidence,
                    actionable = "Prep a high protein meal in advance for ${dayName}s — your pattern suggests you default to convenience food on this day.",
                    detectedAt = getCurrentDate()
                ))
            }

            if (calDeviation > 20) {
                patterns.add(DetectedPattern(
                    id = "high_cal_$dayName",
                    type = "nutrition_day_pattern",
                    title = "$dayName is your surplus day",
                    description = "You eat ${calDeviation.roundToInt()}% more on ${dayName}s — averaging ${dayAvgCal.roundToInt()} kcal. This is likely a social eating pattern.",
                    confidence = confidence,
                    actionable = "Front-load protein earlier on ${dayName}s so surplus calories are less likely to come from low-quality sources.",
                    detectedAt = getCurrentDate()
                ))
            }

            if (proteinDeviation < -20) {
                patterns.add(DetectedPattern(
                    id = "low_protein_$dayName",
                    type = "nutrition_day_pattern",
                    title = "Protein consistently drops on ${dayName}s",
                    description = "Your protein averages ${dayAvgProtein.roundToInt()}g on ${dayName}s versus ${overallAvgProtein.roundToInt()}g on other days. Science: Areta et al 2013 showed that even one day of low protein intake reduces weekly muscle protein synthesis measurably.",
                    confidence = confidence,
                    actionable = "Set a phone alarm for 12pm on ${dayName}s — eat 40g protein before the afternoon starts.",
                    detectedAt = getCurrentDate()
                ))
            }
        }
        return patterns
    }

    // ── PATTERN 2 — NUTRITION TO PERFORMANCE CORRELATION ──────
    // Detects whether eating well the day before predicts
    // better session feel scores
    // Science: Ivy & Portman 2004 — pre-exercise nutrition
    // directly impacts training capacity and RPE
    private fun detectNutritionPerformancePatterns(
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        proteinTarget: Double
    ): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        val nutritionMap = nutritionLog.associateBy { it.date }
        val pairs = mutableListOf<Pair<Double, Int>>()

        trainingLog.filter { it.completed && it.sessionFeel > 0 }.forEach { session ->
            val prevDate = getPreviousDate(session.date) ?: return@forEach
            val prevNutrition = nutritionMap[prevDate] ?: return@forEach
            val proteinPct = if (proteinTarget > 0.0) prevNutrition.protein.toDouble() / proteinTarget else 0.0
            pairs.add(Pair(proteinPct, session.sessionFeel))
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
                    title = "Your training is better after high protein days",
                    description = "When you hit protein targets the day before, your session feel averages ${String.format(java.util.Locale.US, "%.1f", highAvgFeel)}/5 vs ${String.format(java.util.Locale.US, "%.1f", lowAvgFeel)}/5 after low protein days. That is a ${pctDiff}% performance difference.",
                    confidence = minOf(1.0f, pairs.size / 20.0f),
                    actionable = "Prioritise hitting protein the day before your heaviest sessions — Upper A and Lower B specifically.",
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
        if (weightLog.size < 10 || nutritionLog.size < 10) return patterns

        val nutritionMap = nutritionLog.associateBy { it.date }
        val trendData = AlgorithmEngine.calcTrendWeight(weightLog)
        val trendMap = trendData.associateBy { it.date }
        val weightChangePairs = mutableListOf<Pair<Int, Double>>()

        nutritionLog.forEach { entry ->
            val twoDaysLater = getDateDaysFromNow(entry.date, 2) ?: return@forEach
            val currentTrend = trendMap[entry.date]?.trend ?: return@forEach
            val futureTrend = trendMap[twoDaysLater]?.trend ?: return@forEach
            weightChangePairs.add(Pair(entry.carbs.roundToInt(), futureTrend - currentTrend))
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
                    title = "High carb days add ${String.format(java.util.Locale.US, "%.1f", highCarbWeightImpact)}kg to your scale weight",
                    description = "Your scale weight rises ${String.format(java.util.Locale.US, "%.2f", highCarbWeightImpact)}kg two days after high carb days (350g+) versus ${String.format(java.util.Locale.US, "%.2f", lowCarbWeightImpact)}kg after low carb days. This is glycogen-bound water — not fat. Science: Olsson and Saltin 1970 showed each gram of stored glycogen binds 3-4g of water.",
                    confidence = 0.85f,
                    actionable = "Do not panic when the scale rises after high carb training days. Your trend weight is the truth — not the daily number.",
                    detectedAt = getCurrentDate()
                ))
            }
        }
        return patterns
    }

    // ── PATTERN 4 — RECOVERY PATTERNS ─────────────────────────
    // Detects optimal spacing between sessions based on
    // performance data across different recovery windows
    // Science: Bishop et al 2008 — inter-session recovery
    // directly impacts subsequent session quality
    private fun detectRecoveryPatterns(trainingLog: List<TrainingSession>): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        val completedSessions = trainingLog.filter { it.completed && it.sessionFeel > 0 }.sortedBy { it.date }
        if (completedSessions.size < 8) return patterns

        val recoveryPerformancePairs = mutableListOf<Pair<Int, Int>>()

        for (i in 1 until completedSessions.size) {
            val prev = completedSessions[i - 1]
            val current = completedSessions[i]
            val daysBetween = getDaysBetween(prev.date, current.date).toInt()
            if (daysBetween in 1..5) {
                recoveryPerformancePairs.add(Pair(daysBetween, current.sessionFeel))
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
                    title = "You perform best with $days day${if (days > 1) "s" else ""} between sessions",
                    description = "Your session feel score averages ${String.format(java.util.Locale.US, "%.1f", avgFeel)}/5 when you have $days day${if (days > 1) "s" else ""} of rest before training. This is your personal optimal recovery window.",
                    confidence = minOf(1.0f, sessions.size / 8.0f),
                    actionable = "Try to schedule your hardest sessions with at least $days rest day${if (days > 1) "s" else ""} before them.",
                    detectedAt = getCurrentDate()
                ))
            }
        }
        return patterns
    }
}
