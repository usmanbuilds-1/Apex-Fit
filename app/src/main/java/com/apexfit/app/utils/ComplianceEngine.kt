package com.apexfit.app.utils

import com.apexfit.app.data.*
import kotlin.math.roundToInt

object ComplianceEngine {
    // ── COMPLIANCE SCORES ────────────────────────────────────
    fun calcComplianceScores(nutritionLog: List<NutritionEntry>, trainingLog: List<TrainingSession>, targets: NutritionTargets, days: Int = 14): ComplianceResult {
        if (targets.calories <= 0 || targets.protein <= 0) {
            return ComplianceResult(
                calories = 0,
                protein = 0,
                training = 0,
                overall = 0,
                weakestDay = null
            )
        }
        val cutoff = getDateDaysAgo(days)
        val recentNutrition = nutritionLog.filter { it.date >= cutoff }
        val dailyNutrition = recentNutrition
            .groupBy { it.date }
            .map { (_, e) -> Pair(e.sumOf { it.calories }, e.sumOf { it.protein }) }
        val recentTraining = trainingLog.filter { it.date >= cutoff && it.completed }

        val calHits = dailyNutrition.count { (cal, _) -> 
            Math.abs(cal - targets.calories).toDouble() / targets.calories <= 0.10 
        }
        val proteinHits = dailyNutrition.count { (_, protein) -> protein >= targets.protein }
        val expectedSessions = ((days / 7.0) * targets.weeklyTrainingSessions).roundToInt()
        val trainingScore = if (expectedSessions > 0) minOf(100, ((recentTraining.size.toDouble() * 100) / expectedSessions).roundToInt()) else 0
        val calScore = if (dailyNutrition.isNotEmpty()) (calHits * 100) / dailyNutrition.size else 0
        val proteinScore = if (dailyNutrition.isNotEmpty()) (proteinHits * 100) / dailyNutrition.size else 0
        val overall = ((proteinScore * 0.4) + (calScore * 0.35) + (trainingScore * 0.25)).roundToInt()

        val dayNames = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
        val dayScores = mutableMapOf<String, Pair<Int,Int>>()
        val groupedByDate = recentNutrition.groupBy { it.date }

        // Cache LocalDate.parse() once per unique date string (Fix V-M9)
        val dateParseCache = mutableMapOf<String, java.time.LocalDate>()
        groupedByDate.keys.forEach { dateStr ->
            try {
                dateParseCache[dateStr] = java.time.LocalDate.parse(dateStr)
            } catch (e: Exception) {
                // Ignore parse errors safely
            }
        }

        groupedByDate.forEach { (dateStr, entries) ->
            val parsedDate = dateParseCache[dateStr] ?: return@forEach
            val dow = parsedDate.dayOfWeek.value % 7
            val day = dayNames[dow]
            val current = dayScores[day] ?: Pair(0, 0)
            val totalCal = entries.sumOf { it.calories }
            val totalProtein = entries.sumOf { it.protein }
            val hit = if (totalProtein >= targets.protein &&
                Math.abs(totalCal - targets.calories).toDouble() / targets.calories <= 0.10) 1 else 0
            dayScores[day] = Pair(current.first + hit, current.second + 1)
        }
        val weakestDay = dayScores.entries
            .filter { it.value.second > 0 }
            .minByOrNull { it.value.first.toDouble() / it.value.second }?.key

        return ComplianceResult(calScore, proteinScore, trainingScore, overall, weakestDay)
    }

    fun calcStreaks(
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        targets: NutritionTargets
    ): StreakResult {
        var currentStreak = 0
        val todayStr = getCurrentDate()
        val yesterdayStr = try {
            java.time.LocalDate.parse(todayStr).minusDays(1).toString()
        } catch (e: Exception) { "" }
        
        val dailyNutritionMap = nutritionLog
            .groupBy { it.date }
            .mapValues { (_, e) -> Pair(e.sumOf { it.calories }, e.sumOf { it.protein }) }

        var checkDate = yesterdayStr
        val todayTotals = dailyNutritionMap[todayStr]
        if (todayTotals != null &&
            todayTotals.second >= targets.protein * 0.9 &&
            Math.abs(todayTotals.first - targets.calories).toDouble() / targets.calories <= 0.15) {
            checkDate = todayStr
        }
        
        var tempDate = checkDate
        val sortedEntries = dailyNutritionMap.entries.sortedByDescending { it.key }
        for (entry in sortedEntries) {
            if (entry.key > tempDate) continue
            if (entry.key != tempDate) break
            val dayTotals = entry.value
            if (dayTotals.second >= targets.protein * 0.9 &&
                Math.abs(dayTotals.first - targets.calories).toDouble() / targets.calories <= 0.15) {
                currentStreak++
                val nextDate: String? = try {
                    java.time.LocalDate.parse(tempDate).minusDays(1).toString()
                } catch (e: Exception) {
                    null
                }
                if (nextDate == null) break
                tempDate = nextDate
            } else {
                break
            }
        }

        // Weekly adherence: how many of this week's planned sessions are completed
        val currentWeekStart = getDateDaysAgo(java.time.DayOfWeek.from(
            java.time.LocalDate.parse(getCurrentDate())).value - 1)
        val completedThisWeek = trainingLog
            .filter { it.completed && it.date >= currentWeekStart }
            .map { it.date }
            .distinct()
            .size
        val trainingStreak = completedThisWeek
        
        return StreakResult(
            nutrition = StreakInfo(currentStreak),
            training = StreakInfo(trainingStreak)
        )
    }
}
