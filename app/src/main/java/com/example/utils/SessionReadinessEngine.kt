package com.example.utils

object SessionReadinessEngine {

    // ── MASTER READINESS CALCULATOR ───────────────────────────
    // Call this when user opens Train tab
    // Science: Gathercole et al 2015 — daily readiness
    // assessment improves training outcomes by 12-18%
    // when used to autoregulate session intensity
    fun calcSessionReadiness(
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        weightLog: List<WeightEntry>,
        sleepLog: List<SleepEntry> = emptyList(),
        targets: NutritionTargets,
        todaySessionType: String
    ): SessionReadiness? {
        val completed = trainingLog.filter { it.completed }
        val earliestCompleted = completed.minByOrNull { it.date }
        val daysOfHistory = if (earliestCompleted != null) getDaysBetween(earliestCompleted.date, getCurrentDate()) else 0L

        val mostRecentCompleted = completed.maxByOrNull { it.date }
        val daysSinceLastCompleted = if (mostRecentCompleted != null) {
            getDaysBetween(mostRecentCompleted.date, getCurrentDate())
        } else {
            999L
        }

        val fatigueResult = AlgorithmEngine.calcFatigueToFitness(trainingLog)
        val fatigueUnavailable = fatigueResult.status == "unknown" || fatigueResult.ratio == null

        if (completed.size < 14 || daysOfHistory < 14 || daysSinceLastCompleted > 3 || fatigueUnavailable) {
            return null
        }

        val factors = mutableListOf<ReadinessFactor>()
        var totalScore = 0
        var maxScore = 0

        // ── FACTOR 1: YESTERDAY'S NUTRITION (weight 30) ───────
        val yesterday = getPreviousDate(getCurrentDate())
        val yesterdayNutrition = nutritionLog.find { it.date == yesterday }
        val nutritionScore: Int
        val nutritionImpact: String

        if (yesterdayNutrition != null) {
            val caloriePct = yesterdayNutrition.calories.toDouble() / targets.calories
            val proteinPct = yesterdayNutrition.protein.toDouble() / targets.protein
            val combinedPct = (caloriePct * 0.4 + proteinPct * 0.6)
            nutritionScore = when {
                combinedPct >= 0.95 -> 30
                combinedPct >= 0.85 -> 24
                combinedPct >= 0.75 -> 18
                combinedPct >= 0.60 -> 10
                else -> 4
            }
            nutritionImpact = when {
                combinedPct >= 0.95 -> "positive"
                combinedPct >= 0.75 -> "neutral"
                else -> "negative"
            }
            factors.add(ReadinessFactor(
                name = "Yesterday's Nutrition",
                impact = nutritionImpact,
                value = "${yesterdayNutrition.calories} kcal · ${yesterdayNutrition.protein}g protein"
            ))
        } else {
            nutritionScore = 15
            nutritionImpact = "neutral"
            factors.add(ReadinessFactor("Yesterday's Nutrition", "neutral", "Not logged"))
        }
        totalScore += nutritionScore
        maxScore += 30

        // ── FACTOR 2: RECOVERY SINCE LAST SESSION (weight 25) ─
        val lastSession = trainingLog.filter { it.completed }.maxByOrNull { it.date }
        val recoveryScore: Int

        if (lastSession != null) {
            val daysSince = getDaysBetween(lastSession.date, getCurrentDate())
            val lastSessionFatigue = lastSession.exercises.flatMap { it.sets }
                .filter { !it.isWarmup }.map { it.rpe }.average().takeIf { !it.isNaN() } ?: 7.0
            val requiredRecovery = when {
                lastSessionFatigue >= 8.5 -> 2.0
                lastSessionFatigue >= 7.5 -> 1.5
                else -> 1.0
            }
            val recoveryRatio = daysSince / requiredRecovery
            recoveryScore = when {
                recoveryRatio >= 1.5 -> 25
                recoveryRatio >= 1.0 -> 20
                recoveryRatio >= 0.7 -> 12
                else -> 5
            }
            factors.add(ReadinessFactor(
                name = "Recovery",
                impact = if (recoveryScore >= 20) "positive" else if (recoveryScore >= 12) "neutral" else "negative",
                value = "$daysSince day${if (daysSince != 1L) "s" else ""} since last session"
            ))
        } else {
            recoveryScore = 25
            factors.add(ReadinessFactor("Recovery", "positive", "Well rested"))
        }
        totalScore += recoveryScore
        maxScore += 25

        // ── FACTOR 3: CURRENT FATIGUE RATIO (weight 25) ───────
        val fatigueScore = when (fatigueResult.status) {
            "optimal" -> 25
            "low" -> 20
            "detraining" -> 18
            "high" -> 12
            "danger" -> 4
            else -> 15
        }
        factors.add(ReadinessFactor(
            name = "Cumulative Fatigue",
            impact = if (fatigueScore >= 20) "positive" else if (fatigueScore >= 12) "neutral" else "negative",
            value = fatigueResult.statusLabel
        ))
        totalScore += fatigueScore
        maxScore += 25

        // ── FACTOR 4: SLEEP (weight 20 if logged, else skip) ──
        val lastSleep = sleepLog.maxByOrNull { it.date }
        if (lastSleep != null && getDaysBetween(lastSleep.date, getCurrentDate()) <= 1) {
            val sleepScore = when {
                lastSleep.hours >= 8.0 -> 20
                lastSleep.hours >= 7.0 -> 16
                lastSleep.hours >= 6.0 -> 10
                lastSleep.hours >= 5.0 -> 5
                else -> 0
            }
            factors.add(ReadinessFactor(
                name = "Sleep",
                impact = if (sleepScore >= 16) "positive" else if (sleepScore >= 10) "neutral" else "negative",
                value = "${"%.1f".format(lastSleep.hours)} hours"
            ))
            totalScore += sleepScore
            maxScore += 20
        }

        // ── CALCULATE FINAL SCORE ─────────────────────────────
        val finalScore = if (maxScore > 0) ((totalScore.toDouble() / maxScore) * 100).toInt() else 50

        val label: String
        val colorHex: String
        val prediction: String
        val recommendation: String

        when {
            finalScore >= 85 -> {
                label = "Excellent"
                colorHex = "#34D399"
                prediction = "Conditions are optimal for a strong ${todaySessionType} session. Your body is primed."
                recommendation = "Push to the top of your rep ranges today. Consider attempting a PR on your primary lift."
            }
            finalScore >= 70 -> {
                label = "Good"
                colorHex = "#6366F1"
                prediction = "You should perform well today. Minor factors may cap your absolute peak but this is a solid training day."
                recommendation = "Train as programmed. If a set feels unusually hard, stop at RIR 1 rather than pushing to failure."
            }
            finalScore >= 55 -> {
                label = "Moderate"
                colorHex = "#A78BFA"
                prediction = "Some recovery debt detected. You can train but expect performance to be 5-10% below your best."
                recommendation = "Reduce working weight by 5% today. Focus on technique quality and mind-muscle connection rather than load."
            }
            finalScore >= 35 -> {
                label = "Low"
                colorHex = "#F59E0B"
                prediction = "Your readiness is significantly compromised. Pushing hard today risks quality and injury."
                recommendation = "Either take a rest day or reduce volume by 40% — drop to 2 sets per exercise and stay at RIR 3 minimum."
            }
            else -> {
                label = "Poor"
                colorHex = "#E84040"
                prediction = "Multiple recovery indicators are in the red. This is not a productive training day."
                recommendation = "Rest today. Active recovery only — walk, stretch, prioritise sleep tonight and nutrition today."
            }
        }

        return SessionReadiness(finalScore, label, colorHex, prediction, recommendation, factors)
    }
}
