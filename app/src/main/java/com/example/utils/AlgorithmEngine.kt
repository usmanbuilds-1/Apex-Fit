package com.example.utils

object AlgorithmEngine {

    // ── TREND WEIGHT ──────────────────────────────────────────
    // Science: Helms et al. (The Muscle and Strength Pyramid)
    // Filters daily water and glycogen fluctuations using a 14-day Exponential Moving Average (EMA).
    // Smoothing coefficient α = 2 / (N + 1) where N = 14 days, giving α ≈ 0.133.
    fun calcTrendWeight(log: List<WeightEntry>): List<TrendPoint> {
        if (log.isEmpty()) return emptyList()
        val sorted = log.sortedBy { it.date }
        val result = mutableListOf<TrendPoint>()
        sorted.forEachIndexed { index, entry ->
            if (index == 0) {
                result.add(TrendPoint(entry.date, entry.weight, entry.weight))
            } else {
                val prev = result[index - 1].trend
                // Apply Helms' 14-day exponential smoothing weight
                val trend = prev + 0.133 * (entry.weight - prev)
                result.add(TrendPoint(entry.date, entry.weight, Math.round(trend * 100.0) / 100.0))
            }
        }
        return result
    }

    fun getCurrentTrendWeight(log: List<WeightEntry>): Double? {
        return calcTrendWeight(log).lastOrNull()?.trend
    }

    fun getWeightDirection(log: List<WeightEntry>, days: Int = 14): String {
        val trend = calcTrendWeight(log)
        if (trend.size < 2) return "insufficient_data"
        val recent = trend.takeLast(days)
        val change = recent.last().trend - recent.first().trend
        return when {
            change > 0.3 -> "gaining"
            change < -0.3 -> "losing"
            else -> "maintaining"
        }
    }

    // ── ADAPTIVE TDEE ─────────────────────────────────────────
    // Science: Hall (2012) - Adaptive metabolic model of energy homeostasis.
    // Daily TDEE = Avg Intake - (Weight change rate * 7700 kcal/kg).
    // Fallback: Mifflin-St Jeor BMR equation (Mifflin MD, et al. 1990) scaled by dynamic activity multiplier.
    fun calcAdaptiveTDEE(
        weightLog: List<WeightEntry>,
        nutritionLog: List<NutritionEntry>,
        windowDays: Int = 14,
        heightCm: Double = com.example.UserDefaults.HEIGHT_CM,
        ageYears: Int = com.example.UserDefaults.AGE_YEARS,
        biologicalSex: String = "male",
        weeklyWorkouts: Int = 4
    ): TDEEResult {
        val latestWeight = weightLog.lastOrNull()?.weight ?: com.example.UserDefaults.WEIGHT_KG
        
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
        val fallbackTdee = (bmrBaseline * activityMultiplier).toInt()

        if (weightLog.size < 7 || nutritionLog.size < 7) {
            return TDEEResult(fallbackTdee, "low (Mifflin-St Jeor)", 0, 0.0)
        }

        val cutoff = getDateDaysAgo(windowDays)
        val recentWeight = weightLog.filter { it.date >= cutoff }.sortedBy { it.date }
        val recentNutrition = nutritionLog.filter { it.date >= cutoff }

        if (recentWeight.size < 2 || recentNutrition.size < 3) {
            return TDEEResult(fallbackTdee, "low (Mifflin-St Jeor)", recentNutrition.map { it.calories }.average().takeIf { !it.isNaN() }?.toInt() ?: 0, 0.0)
        }

        val avgCalories = recentNutrition.map { it.calories }.average()
        val trendData = calcTrendWeight(recentWeight)
        val weightChangeKg = trendData.last().trend - trendData.first().trend
        
        // CRITICAL SCIENTIFIC FIX: Divisor must be actual calendar days between weights, not the number of logs
        val oldestEntry = recentWeight.first()
        val newestEntry = recentWeight.last()
        val daysBetween = getDaysBetween(oldestEntry.date, newestEntry.date).coerceAtLeast(1L)
        
        // Weight gain or loss translates to calorie imbalance per day (1kg = 7700 kcal energy density, Hall 2012)
        val calorieImbalancePerDay = (weightChangeKg * 7700.0) / daysBetween
        val tdee = (avgCalories - calorieImbalancePerDay).toInt()
        
        val confidence = when {
            recentNutrition.size >= 12 && recentWeight.size >= 10 -> "high"
            recentNutrition.size >= 7 && recentWeight.size >= 5 -> "medium"
            else -> "low"
        }
        return TDEEResult(tdee, confidence, avgCalories.toInt(), Math.round(weightChangeKg * 100.0) / 100.0)
    }

    fun suggestCaloricTarget(tdee: Int, goal: String, rateKgPerWeek: Double = 0.25): Int {
        val dailyAdjustment = ((rateKgPerWeek * 7700) / 7).toInt()
        val g = goal.lowercase()
        return when {
            g.contains("lose") || g.contains("cut") || g.contains("deficit") -> tdee - dailyAdjustment
            g.contains("gain") || g.contains("bulk") || g.contains("surplus") -> tdee + dailyAdjustment
            else -> tdee
        }
    }

    // ── COMPLIANCE SCORES ────────────────────────────────────
    fun calcComplianceScores(nutritionLog: List<NutritionEntry>, trainingLog: List<TrainingSession>, targets: NutritionTargets, days: Int = 14): ComplianceResult {
        val cutoff = getDateDaysAgo(days)
        val recentNutrition = nutritionLog.filter { it.date >= cutoff }
        val recentTraining = trainingLog.filter { it.date >= cutoff && it.completed }

        val calHits = recentNutrition.count { Math.abs(it.calories - targets.calories).toDouble() / targets.calories <= 0.1 }
        val proteinHits = recentNutrition.count { it.protein >= targets.protein }
        val expectedSessions = ((days / 7.0) * targets.weeklyTrainingSessions).toInt()
        val trainingScore = if (expectedSessions > 0) minOf(100, (recentTraining.size * 100) / expectedSessions) else 0
        val calScore = if (recentNutrition.isNotEmpty()) (calHits * 100) / recentNutrition.size else 0
        val proteinScore = if (recentNutrition.isNotEmpty()) (proteinHits * 100) / recentNutrition.size else 0
        val overall = ((proteinScore * 0.4) + (calScore * 0.35) + (trainingScore * 0.25)).toInt()

        val dayNames = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
        val dayScores = mutableMapOf<String, Pair<Int,Int>>()
        recentNutrition.forEach { entry ->
            try {
                val cal = java.util.Calendar.getInstance()
                cal.time = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(entry.date) ?: return@forEach
                val day = dayNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                val current = dayScores[day] ?: Pair(0, 0)
                val hit = if (entry.protein >= targets.protein && Math.abs(entry.calories - targets.calories).toDouble() / targets.calories <= 0.15) 1 else 0
                dayScores[day] = Pair(current.first + hit, current.second + 1)
            } catch (e: Exception) {
                // Ignore parse errors safely
            }
        }
        val weakestDay = dayScores.entries
            .filter { it.value.second > 0 }
            .minByOrNull { it.value.first.toDouble() / it.value.second }?.key

        return ComplianceResult(calScore, proteinScore, trainingScore, overall, weakestDay)
    }

    // ── PLATEAU DETECTION ─────────────────────────────────────
    fun detectPlateau(weightLog: List<WeightEntry>, nutritionLog: List<NutritionEntry>, trainingLog: List<TrainingSession>, windowDays: Int = 10): PlateauResult {
        val trend = calcTrendWeight(weightLog)
        if (trend.size < windowDays) return PlateauResult(false)
        val recent = trend.takeLast(windowDays)
        val change = Math.abs(recent.last().trend - recent.first().trend)
        val cutoff = getDateDaysAgo(windowDays)
        val recentLogs = nutritionLog.filter { it.date >= cutoff }
        val loggingConsistency = recentLogs.size.toDouble() / windowDays
        if (change >= 0.3) return PlateauResult(false)
        if (loggingConsistency < 0.7) return PlateauResult(false)

        val volumeRecent = trainingLog.filter { it.completed && it.date >= cutoff }
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { !it.isWarmup }
            .sumOf { it.weight * it.reps }

        val volumeEarlier = trainingLog.filter { it.completed && it.date < cutoff }
            .take(windowDays)
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { !it.isWarmup }
            .sumOf { it.weight * it.reps }

        if (volumeEarlier == 0.0) return PlateauResult(false)
        if (volumeRecent < volumeEarlier * 0.95) return PlateauResult(false)

        return PlateauResult(
            plateau = true,
            severity = if (windowDays >= 14) "confirmed" else "early",
            interventions = listOf(
                "Reduce calories by 100-150 kcal/day for 1 week",
                "Add one Zone 2 cardio session",
                "Try a 1-day refeed at maintenance calories",
                "Weigh food for 3 days to recheck portions"
            )
        )
    }

    // ── FATIGUE TO FITNESS RATIO ──────────────────────────────
    fun calcFatigueToFitness(trainingLog: List<TrainingSession>): FatigueResult {
        val completed = trainingLog.filter { it.completed }
        val earliestCompleted = completed.minByOrNull { it.date }
        val daysOfHistory = if (earliestCompleted != null) getDaysBetween(earliestCompleted.date, getCurrentDate()) else 0L

        val acuteCutoff = getDateDaysAgo(7)
        val hasPreAcuteWorkouts = completed.any { it.date < acuteCutoff }

        val isSparse = completed.size < 7 || daysOfHistory < 7 || !hasPreAcuteWorkouts

        if (isSparse) {
            return FatigueResult(
                ratio = null,
                status = "unknown",
                statusLabel = "No Data",
                recommendation = "Log more workouts to activate fatigue tracking",
                acuteLoad = 0.0,
                chronicLoad = 0.0
            )
        }

        val sessionLoads = completed.map { session ->
            val totalVolume = session.exercises.flatMap { it.sets }
                .filter { !it.isWarmup }
                .sumOf { it.weight * it.reps }
            val avgRPE = session.exercises.flatMap { it.sets }.map { it.rpe }.average().takeIf { !it.isNaN() } ?: 7.0
            val feelModifier = session.sessionFeel / 3.0
            Pair(session.date, totalVolume * (avgRPE / 7.0) * feelModifier)
        }.sortedBy { it.first }

        val chronicCutoff = getDateDaysAgo(28)
        val acuteLoad = sessionLoads.filter { it.first >= acuteCutoff }.sumOf { it.second }
        val chronicSessions = sessionLoads.filter { it.first >= chronicCutoff }
        val chronicLoad = if (chronicSessions.isNotEmpty()) chronicSessions.sumOf { it.second } / 4.0 else acuteLoad
        val ratio = if (chronicLoad > 0) acuteLoad / chronicLoad else null

        val status: String
        val statusLabel: String
        val recommendation: String
        when {
            ratio == null -> { status = "unknown"; statusLabel = "No Data"; recommendation = "Log more workouts to activate fatigue tracking" }
            ratio < 0.6 -> { status = "detraining"; statusLabel = "Undertraining"; recommendation = "Increase training load this week" }
            ratio < 0.8 -> { status = "low"; statusLabel = "Below optimal"; recommendation = "Slightly increase volume or intensity" }
            ratio <= 1.3 -> { status = "optimal"; statusLabel = "Sweet spot"; recommendation = "Training load is perfect — maintain this" }
            ratio <= 1.5 -> { status = "high"; statusLabel = "Pushing hard"; recommendation = "Back off 10-15% next session" }
            else -> { status = "danger"; statusLabel = "Overreaching"; recommendation = "Reduce load significantly — injury risk elevated" }
        }
        return FatigueResult(ratio, status, statusLabel, recommendation, acuteLoad, chronicLoad)
    }

    // ── PERSONAL RECORDS ──────────────────────────────────────
    fun checkPersonalRecords(trainingLog: List<TrainingSession>, exerciseId: String): PRResult {
        val sessions = trainingLog.filter { s -> s.completed && s.exercises.any { it.id == exerciseId } }.sortedBy { it.date }
        if (sessions.size < 2) return PRResult(false)

        var prevMaxWeight = 0.0
        var prevMaxVolume = 0.0
        var prevMax1RM = 0.0

        sessions.dropLast(1).forEach { session ->
            val ex = session.exercises.find { it.id == exerciseId } ?: return@forEach
            val working = ex.sets.filter { !it.isWarmup && it.completed }
            prevMaxWeight = maxOf(prevMaxWeight, working.maxOfOrNull { it.weight } ?: 0.0)
            prevMaxVolume = maxOf(prevMaxVolume, working.sumOf { it.weight * it.reps })
            prevMax1RM = maxOf(prevMax1RM, working.filter { it.reps <= 12 }.maxOfOrNull { it.weight * (1 + it.reps / 30.0) } ?: 0.0)
        }

        val latest = sessions.last().exercises.find { it.id == exerciseId } ?: return PRResult(false)
        val latestWorking = latest.sets.filter { !it.isWarmup && it.completed }
        val latestWeight = latestWorking.maxOfOrNull { it.weight } ?: 0.0
        val latestVolume = latestWorking.sumOf { it.weight * it.reps }
        val latest1RM = latestWorking.filter { it.reps <= 12 }.maxOfOrNull { it.weight * (1 + it.reps / 30.0) } ?: 0.0

        val newPRs = mutableListOf<PREntry>()
        if (latestWeight > prevMaxWeight) newPRs.add(PREntry("weight", "Weight PR", "${latestWeight}kg", "${prevMaxWeight}kg"))
        if (latestVolume > prevMaxVolume) newPRs.add(PREntry("volume", "Volume PR", "${latestVolume.toInt()}kg", "${prevMaxVolume.toInt()}kg"))
        if (latest1RM > prevMax1RM + 0.5) newPRs.add(PREntry("estimated_1rm", "Estimated 1RM PR", "${"%.1f".format(latest1RM)}kg", "${"%.1f".format(prevMax1RM)}kg"))

        return PRResult(newPRs.isNotEmpty(), newPRs)
    }

    // ── DIMINISHING RETURNS ───────────────────────────────────
    fun detectDiminishingReturns(trainingLog: List<TrainingSession>, exerciseId: String): DiminishingResult {
        val sessions = trainingLog.filter { s -> s.completed && s.exercises.any { it.id == exerciseId } }.sortedBy { it.date }
        if (sessions.size < 4) return DiminishingResult("insufficient_data", "Need 4+ sessions")

        val weights = sessions.takeLast(6).mapNotNull { session ->
            session.exercises.find { it.id == exerciseId }?.sets?.filter { !it.isWarmup }?.maxOfOrNull { it.weight }
        }
        if (weights.size < 3) return DiminishingResult("insufficient_data", "Need more data")

        val slope = linearRegressionSlope(weights)
        val last3Variance = (weights.takeLast(3).maxOrNull() ?: 0.0) - (weights.takeLast(3).minOrNull() ?: 0.0)
        val avgRPERecent = sessions.takeLast(3).flatMap { s -> s.exercises.find { it.id == exerciseId }?.sets?.map { it.rpe } ?: emptyList() }.average()
        val avgRPEEarlier = sessions.take(3).flatMap { s -> s.exercises.find { it.id == exerciseId }?.sets?.map { it.rpe } ?: emptyList() }.average()
        val rpeIncreasing = avgRPERecent > avgRPEEarlier + 0.5

        return when {
            slope > 0.5 -> DiminishingResult("progressing", "Gaining ~${"%.1f".format(slope)}kg per session — keep going")
            slope > 0.1 -> DiminishingResult("slowing", "Progress slowing — consider a rep focus phase", listOf("Drop weight 10%, push for more reps", "Add a fourth set"))
            last3Variance < 1.25 && rpeIncreasing -> DiminishingResult("stalled_with_fatigue", "Weight stalled and getting harder — fatigue likely", listOf("Mini deload 5-7 days then retest", "Check sleep and nutrition on training days"))
            else -> DiminishingResult("plateau", "No progress in recent sessions — change stimulus", listOf("Change rep range", "Switch to a variation", "Add tempo manipulation — 4-sec eccentric", "Increase frequency"))
        }
    }

    // ── VOLUME LOAD ───────────────────────────────────────────
    fun calcWeeklyVolumePerMuscle(trainingLog: List<TrainingSession>, weeks: Int = 4): Map<String, List<Pair<String, Double>>> {
        val result = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        val weekGroups = trainingLog.filter { it.completed }.groupBy { getWeekKey(it.date) }
        weekGroups.entries.sortedBy { it.key }.takeLast(weeks).forEach { (week, sessions) ->
            val muscleVolume = mutableMapOf<String, Double>()
            sessions.forEach { session ->
                session.exercises.forEach { exercise ->
                    val volume = exercise.sets.filter { !it.isWarmup }.sumOf { it.weight * it.reps }
                    muscleVolume[exercise.muscleGroup] = (muscleVolume[exercise.muscleGroup] ?: 0.0) + volume
                }
            }
            muscleVolume.forEach { (muscle, volume) ->
                result.getOrPut(muscle) { mutableListOf() }.add(Pair(week, volume))
            }
        }
        return result
    }

    // ── MUSCLE HEATMAP ────────────────────────────────────────
    fun calcMuscleHeatmap(trainingLog: List<TrainingSession>, days: Int = 7): Map<String, HeatmapEntry> {
        val cutoff = getDateDaysAgo(days)
        val recent = trainingLog.filter { it.completed && it.date >= cutoff }
        val muscleVolume = mutableMapOf<String, Double>()
        recent.forEach { session ->
            session.exercises.forEach { exercise ->
                val volume = exercise.sets.filter { !it.isWarmup }.sumOf { it.weight * it.reps }
                muscleVolume[exercise.muscleGroup] = (muscleVolume[exercise.muscleGroup] ?: 0.0) + volume
            }
        }
        val maxVolume = muscleVolume.values.maxOrNull() ?: 1.0
        val allMuscles = listOf("chest","back","side_delt","rear_delt","front_delt","bicep","tricep","quad","hamstring","glute","calf","core")
        return allMuscles.associateWith { muscle ->
            val vol = muscleVolume[muscle] ?: 0.0
            val intensity = ((vol / maxVolume) * 100).toInt()
            HeatmapEntry(
                volume = vol.toInt(),
                intensity = intensity,
                level = when { intensity >= 80 -> "high"; intensity >= 40 -> "medium"; intensity >= 10 -> "low"; else -> "untrained" },
                colorHex = when { intensity >= 80 -> "#E84040"; intensity >= 40 -> "#E8A020"; intensity >= 10 -> "#2EC46A"; else -> "#252535" }
            )
        }
    }

    // ── DELOAD PLANNER ────────────────────────────────────────
    fun calcDeloadRecommendation(trainingLog: List<TrainingSession>, complianceScore: Int): DeloadResult {
        val fatigue = calcFatigueToFitness(trainingLog)
        val recent6 = trainingLog.filter { it.completed }.sortedByDescending { it.date }.take(6)
        val avgFeel = recent6.map { it.sessionFeel }.average().takeIf { !it.isNaN() } ?: 3.0
        var signals = 0
        if ((fatigue.ratio ?: 0.0) >= 1.3) signals++
        if (avgFeel <= 2.5) signals++
        if (complianceScore < 60) signals++
        if ((fatigue.ratio ?: 0.0) >= 1.5) signals += 2
        return when {
            signals >= 3 || (fatigue.ratio ?: 0.0) >= 1.5 -> DeloadResult("deload_now", "immediate", signals, listOf("Keep same exercises", "Reduce sets by half", "Drop weight 20%", "No sets to failure"))
            signals == 2 -> DeloadResult("deload_soon", "this_week", signals, listOf("Reduce to 2-3 sets per exercise", "Stay well within RIR"))
            else -> DeloadResult("continue", "none", signals)
        }
    }

    // ── SMART REST TIME ───────────────────────────────────────
    fun calcSmartRestTime(rpe: Int, exerciseId: String, defaultRestSeconds: Int): Int {
        var adjusted = defaultRestSeconds
        adjusted = when {
            rpe >= 9 -> (adjusted * 1.3).toInt()
            rpe <= 6 -> (adjusted * 0.8).toInt()
            else -> adjusted
        }
        val heavyCompounds = listOf("squat","deadlift","ohp","bent-over-row")
        val isolationExercises = listOf("cable-lateral-raise","face-pull","incline-db-curl","calf-raise")
        if (exerciseId in heavyCompounds) adjusted = maxOf(adjusted, 90)
        if (exerciseId in isolationExercises) adjusted = minOf(adjusted, 90)
        return adjusted
    }

    // ── HYPERTROPHY QUALITY SCORE ─────────────────────────────
    fun calcEffectiveSets(trainingLog: List<TrainingSession>, days: Int = 7): Map<String, Double> {
        val cutoff = getDateDaysAgo(days)
        val recent = trainingLog.filter { it.completed && it.date >= cutoff }
        val effectiveSets = mutableMapOf<String, Double>()
        recent.forEach { session ->
            session.exercises.forEach { exercise ->
                val working = exercise.sets.filter { !it.isWarmup && it.completed }
                working.forEach { set ->
                    val rpeModifier = when {
                        set.rpe >= 10 -> 1.0
                        set.rpe == 9 -> 0.9
                        set.rpe == 8 -> 0.75
                        set.rpe == 7 -> 0.5
                        else -> 0.0
                    }
                    val score = 1.0 * rpeModifier
                    effectiveSets[exercise.muscleGroup] = (effectiveSets[exercise.muscleGroup] ?: 0.0) + score
                }
            }
        }
        return effectiveSets
    }

    // ── INJURY RISK SIGNALS ───────────────────────────────────
    fun detectInjuryRiskSignals(trainingLog: List<TrainingSession>): List<String> {
        val signals = mutableListOf<String>()
        val recent = trainingLog.filter { it.completed }.sortedByDescending { it.date }.take(6)

        val highRPESets = recent.flatMap { it.exercises }.flatMap { it.sets }.count { it.rpe >= 9 }
        if (highRPESets >= 6) signals.add("High intensity clustering — connective tissue recovery may be lagging")

        val fatigue = calcFatigueToFitness(trainingLog)
        if ((fatigue.ratio ?: 0.0) >= 1.5) signals.add("Volume load too high relative to baseline — back off this week")

        val muscleFrequency = mutableMapOf<String, Int>()
        val cutoff = getDateDaysAgo(7)
        trainingLog.filter { it.completed && it.date >= cutoff }.forEach { session ->
            session.exercises.forEach { exercise ->
                muscleFrequency[exercise.muscleGroup] = (muscleFrequency[exercise.muscleGroup] ?: 0) + 1
            }
        }
        muscleFrequency.entries.filter { it.value > 4 }.forEach { (muscle, count) ->
            signals.add("$muscle trained $count times this week — add rest before next session")
        }
        return signals
    }

    // ── UTILITIES ─────────────────────────────────────────────

    /**
     * Estimates single working set duration (Time Under Tension) based on targeted rep ranges
     * to avoid unrealistic static estimates (e.g., hardcoded 0.75 min across all loads).
     *
     * Reference: Helms et al. training tempo standard guidelines (controlled eccentrics).
     */
    fun estimateSetDurationMinutes(repsMin: Int, repsMax: Int): Double {
        val avgReps = (repsMin + repsMax) / 2.0
        val timePerRepSeconds = when {
            avgReps <= 5 -> 4.5    // Strength focus: slow eccentrics, high stability, pause
            avgReps <= 12 -> 3.0   // Hypertrophy focus: standard controlled eccentrics
            else -> 2.0            // Endurance focus: higher speed metabolic pump reps
        }
        return (avgReps * timePerRepSeconds) / 60.0
    }

    private fun linearRegressionSlope(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val n = values.size
        val xMean = (n - 1) / 2.0
        val yMean = values.average()
        val numerator = values.indices.sumOf { i -> (i - xMean) * (values[i] - yMean) }
        val denominator = values.indices.sumOf { i -> (i - xMean) * (i - xMean) }
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }

    fun calcStreaks(
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        targets: NutritionTargets
    ): StreakResult {
        var currentStreak = 0
        val todayStr = getCurrentDate()
        val yesterdayStr = getPreviousDate(todayStr)
        
        val todayEntry = nutritionLog.find { it.date == todayStr }
        var checkDate = yesterdayStr
        if (todayEntry != null && todayEntry.protein >= targets.protein * 0.9 && Math.abs(todayEntry.calories - targets.calories).toDouble() / targets.calories <= 0.15) {
            checkDate = todayStr
        }
        
        var tempDate = checkDate
        var iterations = 0
        while (iterations < 1000) {
            val entry = nutritionLog.find { it.date == tempDate }
            if (entry != null && entry.protein >= targets.protein * 0.9 && Math.abs(entry.calories - targets.calories).toDouble() / targets.calories <= 0.15) {
                currentStreak++
                val nextDate = getPreviousDate(tempDate)
                if (nextDate == tempDate) {
                    break
                }
                tempDate = nextDate
                iterations++
            } else {
                break
            }
        }
        
        return StreakResult(
            nutrition = StreakInfo(currentStreak),
            training = StreakInfo(0)
        )
    }
}
