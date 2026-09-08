package com.apexfit.app.utils

import com.apexfit.app.UserDefaults
import com.apexfit.app.data.*
import kotlin.math.roundToInt

fun calcMacroTargets(
    calorieTarget: Int,
    bodyWeightKg: Double,
    goal: String,
    heightCm: Double = UserDefaults.HEIGHT_CM,
    sex: String = "male"
): com.apexfit.app.data.NutritionTargets = AlgorithmEngine.calcMacroTargets(calorieTarget, bodyWeightKg, goal, heightCm, sex)

data class GoalTimeline(
    val weeksToGoal: Int,
    val weeklyChangeKg: Double,
    val isRealistic: Boolean,
    val adjustedGoalWeightKg: Double,
    val summaryLine: String
)

object AlgorithmEngine {

    fun estimateLBM(weightKg: Double, heightCm: Double, sex: String): Double {
        // Boer formula (Boer, 1984)
        return if (sex.equals("female", ignoreCase = true)) {
            (0.252 * weightKg) + (0.473 * heightCm) - 48.3
        } else {
            (0.407 * weightKg) + (0.267 * heightCm) - 19.2
        }
    }

    fun calcMacroTargets(
        calorieTarget: Int,
        bodyWeightKg: Double,
        goal: String,
        heightCm: Double = UserDefaults.HEIGHT_CM,
        sex: String = "male"
    ): com.apexfit.app.data.NutritionTargets {
        val lbm = estimateLBM(bodyWeightKg, heightCm, sex)
        val basisKg = minOf(lbm, bodyWeightKg)  // never exceed total weight

        val proteinMultiplier = when (goal.lowercase()) {
            "lose fat"        -> 2.4
            "recomposition"   -> 2.8
            "gain muscle"     -> 2.0
            else              -> 1.6
        }
        val proteinG = (basisKg * proteinMultiplier)
            .roundToInt()
            .coerceIn(100, 350)  // raise cap from 250 to 350

        val fatG = (bodyWeightKg * when (goal.lowercase()) {
            "gain muscle", "recomposition" -> 1.0   // 1g/kg supports hormonal function
            else -> 0.8
        }).roundToInt().coerceAtLeast(40)

        val proteinCals = proteinG * 4
        val fatCals     = fatG * 9
        val carbCals    = (calorieTarget - proteinCals - fatCals).coerceAtLeast(0)
        val carbsG      = (carbCals / 4).coerceIn(50, 600)

        val weeklySessions = when (goal.lowercase()) {
            "gain muscle" -> 4
            "lose fat"    -> 5
            else          -> 4
        }
        return com.apexfit.app.data.NutritionTargets(
            calories = calorieTarget,
            protein = proteinG,
            carbs = carbsG,
            fat = fatG,
            weeklyTrainingSessions = weeklySessions
        )
    }

    fun calcCycledTargets(
        weeklyCalorieTarget: Int,   // total weekly calories / 7 = flat daily
        weeklyTrainingSessions: Int,
        goal: String,
        bodyWeightKg: Double,
        heightCm: Double,
        sex: String
    ): TrainingDayTargets {
        val safeWeeklySessions = weeklyTrainingSessions.coerceIn(1, 6)
        val restDays = 7 - safeWeeklySessions
        val totalWeekly = weeklyCalorieTarget * 7

        val trainingBonus = when (goal.lowercase()) {
            "gain muscle"   -> 200
            "lose fat"      -> 100
            "recomposition" -> 150
            else -> 0
        }
        val trainingDayCals = weeklyCalorieTarget + trainingBonus
        val restDayCals = ((totalWeekly - (trainingDayCals * safeWeeklySessions))
            .toDouble() / restDays).roundToInt().coerceAtLeast(1000)

        val lbm = estimateLBM(bodyWeightKg, heightCm, sex)
        val trainingProtein = (lbm * 2.2).roundToInt()  // higher on training days
        val restProtein = (lbm * 1.8).roundToInt()

        val fat = (bodyWeightKg * 0.9).roundToInt().coerceAtLeast(40)

        val trainingCarbs = (((trainingDayCals - (trainingProtein * 4) - (fat * 9)).toDouble()) / 4.0)
            .coerceAtLeast(50.0).roundToInt()
        val restCarbs = (((restDayCals - (restProtein * 4) - (fat * 9)).toDouble()) / 4.0)
            .coerceAtLeast(30.0).roundToInt()

        return TrainingDayTargets(
            trainingDayCals, restDayCals,
            trainingProtein, restProtein,
            trainingCarbs, restCarbs,
            fat
        )
    }

    fun calcGoalTimeline(
        currentWeightKg: Double,
        goalWeightKg: Double,
        resolvedGoal: String,
        heightCm: Double,
        sex: String
    ): GoalTimeline {
        val weeklyRate = when (resolvedGoal.lowercase()) {
            "gain muscle"   -> (currentWeightKg * 0.004).coerceIn(0.15, 0.35)
            "lose fat"      -> (currentWeightKg * 0.008).coerceIn(0.30, 0.70)
            "recomposition" -> 0.05
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
            resolvedGoal.equals("Recomposition", ignoreCase = true) ->
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
            weeksToGoal           = weeksRaw.coerceAtMost(104),
            weeklyChangeKg        = weeklyRate,
            isRealistic           = isRealistic,
            adjustedGoalWeightKg  = adjustedGoal,
            summaryLine           = summaryLine
        )
    }


    // ── TREND WEIGHT ──────────────────────────────────────────
    // Science: Helms et al. (The Muscle and Strength Pyramid)
    // Filters daily water and glycogen fluctuations using a 14-day Exponential Moving Average (EMA).
    // Smoothing coefficient α = 2 / (N + 1) where N = 14 days, giving α ≈ 0.133.
    fun calcTrendWeight(log: List<WeightEntry>): List<TrendPoint> {
        if (log.isEmpty()) return emptyList()

        val dailyAveraged = log
            .groupBy { it.date }
            .map { (date, entries) ->
                WeightEntry(date = date, weight = entries.map { it.weight }.average())
            }
            .sortedBy { it.date }

        val result = mutableListOf<TrendPoint>()
        dailyAveraged.forEachIndexed { index, entry ->
            if (index == 0) {
                result.add(TrendPoint(entry.date, entry.weight, entry.weight))
            } else {
                val prev = result[index - 1].trend
                val trend = prev + 0.133 * (entry.weight - prev)
                result.add(TrendPoint(entry.date, entry.weight,
                    Math.round(trend * 100.0) / 100.0))
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

        if (weightLog.size < 7 || nutritionLog.size < 7) {
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
        val trendData = calcTrendWeight(recentWeight)
        val weightChangeKg = trendData.last().trend - trendData.first().trend
        
        // CRITICAL SCIENTIFIC FIX: Divisor must be actual calendar days between weights, not the number of logs
        val oldestEntry = recentWeight.first()
        val newestEntry = recentWeight.last()
        val daysBetween = getDaysBetween(oldestEntry.date, newestEntry.date).coerceAtLeast(1L)
        
        // Weight gain or loss translates to calorie imbalance per day (1kg = 7700 kcal energy density, Hall 2012)
        val calorieImbalancePerDay = (weightChangeKg * AppConstants.CALORIES_PER_KG_BODYFAT) / daysBetween
        val tdee = (avgCalories - calorieImbalancePerDay).roundToInt().coerceAtLeast(800)
        
        val confidence = when {
            recentNutrition.size >= 12 && recentWeight.size >= 10 -> "high"
            recentNutrition.size >= 7 && recentWeight.size >= 5 -> "medium"
            else -> "low"
        }
        return TDEEResult(tdee, confidence, avgCalories.roundToInt(), Math.round(weightChangeKg * 100.0) / 100.0)
    }

    fun suggestCaloricTarget(
        tdee: Int,
        goal: String,
        currentWeightKg: Double,
        goalWeightKg: Double
    ): Int {
        val distanceKg = Math.abs(goalWeightKg - currentWeightKg)

        val rateKgPerWeek = when (goal.lowercase()) {
            "gain muscle" -> (currentWeightKg * 0.004).coerceIn(0.15, 0.35)
            "lose fat"    -> (currentWeightKg * 0.008).coerceIn(0.3, 0.7)
            "recomposition" -> 0.0
            else          -> 0.0
        }

        val dailyAdjustment = ((rateKgPerWeek * 7700.0) / 7.0).roundToInt()

        return when (goal.lowercase()) {
            "gain muscle"   -> tdee + dailyAdjustment
            "lose fat"      -> tdee - dailyAdjustment
            "recomposition" -> tdee  // maintenance
            else            -> tdee
        }
    }

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
        recentNutrition.groupBy { it.date }.forEach { (dateStr, entries) ->
            try {
                val cal = java.util.Calendar.getInstance()
                cal.time = com.apexfit.app.utils.DateTimeUtils.parseDate(dateStr) ?: return@forEach
                val day = dayNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                val current = dayScores[day] ?: Pair(0, 0)
                val totalCal = entries.sumOf { it.calories }
                val totalProtein = entries.sumOf { it.protein }
                val hit = if (totalProtein >= targets.protein &&
                    Math.abs(totalCal - targets.calories).toDouble() / targets.calories <= 0.10) 1 else 0
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
        val loggingConsistency = recentLogs.map { it.date }.distinct().size.toDouble() / windowDays
        if (change >= 0.3) return PlateauResult(false)
        if (loggingConsistency < 0.7) return PlateauResult(false)

        val volumeRecent = trainingLog.filter { it.completed && it.date >= cutoff }
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { !it.isWarmup }
            .sumOf { it.weight * it.reps }

        val earlierCutoff = getDateDaysAgo(windowDays * 2)
        val volumeEarlier = trainingLog
            .filter { it.completed && it.date >= earlierCutoff && it.date < cutoff }
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { !it.isWarmup }
            .sumOf { it.weight * it.reps }

        if (volumeEarlier == 0.0) return PlateauResult(false)
        if (volumeRecent < volumeEarlier * 0.95) return PlateauResult(false)

        var daysStalled = 0
        if (trend.isNotEmpty()) {
            val currentTrend = trend.last().trend
            val lastChangeIndex = trend.indexOfLast { Math.abs(currentTrend - it.trend) > 0.1 }
            if (lastChangeIndex != -1) {
                daysStalled = getDaysBetween(trend[lastChangeIndex].date, trend.last().date).toInt()
            } else {
                daysStalled = getDaysBetween(trend.first().date, trend.last().date).toInt()
            }
        }

        return PlateauResult(
            isPlateaued = true,
            severity = if (windowDays >= 14) "confirmed" else "early",
            interventions = listOf(
                "Reduce calories by 100-150 kcal/day for 1 week",
                "Add one Zone 2 cardio session",
                "Try a 1-day refeed at maintenance calories",
                "Weigh food for 3 days to recheck portions"
            ),
            daysStalled = daysStalled
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
            val totalSets = session.exercises.flatMap { it.sets }
                .count { !it.isWarmup && it.completed }
            val avgRPE = session.exercises.flatMap { it.sets }.filter { !it.isWarmup && it.completed }.map { it.rpe }.average().takeIf { !it.isNaN() } ?: 7.0
            val feelModifier = when {
                session.sessionFeel == 0 -> 1.0
                session.sessionFeel <= 2 -> 1.4 - (session.sessionFeel - 1) * 0.2   // feel=1→1.4, feel=2→1.2
                session.sessionFeel == 3 -> 1.0
                session.sessionFeel <= 5 -> 1.0 - (session.sessionFeel - 3) * 0.2   // feel=4→0.8, feel=5→0.6
                else -> 1.0
            }
            Pair(session.date, totalSets.toDouble() * (avgRPE / 7.0) * feelModifier)
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
                val canonicalMuscle = com.apexfit.app.utils.MuscleAliases.getCanonical(exercise.muscleGroup)
                val volume = exercise.sets.filter { !it.isWarmup }.sumOf { it.weight * it.reps }
                muscleVolume[canonicalMuscle] = (muscleVolume[canonicalMuscle] ?: 0.0) + volume
            }
        }
        val maxVolume = muscleVolume.values.maxOrNull() ?: 1.0
        val allMuscles = MuscleGroups.ALL.map { com.apexfit.app.utils.MuscleAliases.getCanonical(it) }
        return allMuscles.associateWith { muscle ->
            val vol = muscleVolume[muscle] ?: 0.0
            val intensity = ((vol / maxVolume) * 100).roundToInt()
            HeatmapEntry(
                volume = vol.roundToInt(),
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

        val highRPEDays = recent.filter { session ->
            session.exercises.flatMap { it.sets }.any { it.rpe >= 9 }
        }.map { it.date }.distinct().count()
        
        if (highRPEDays >= 4) signals.add("High intensity clustering — heavy RPE sets performed on $highRPEDays distinct days recently")

        val fatigue = calcFatigueToFitness(trainingLog)
        if ((fatigue.ratio ?: 0.0) >= 1.5) signals.add("Volume load too high relative to baseline — back off this week")

        val muscleDays = mutableMapOf<String, MutableSet<String>>()
        val cutoff = getDateDaysAgo(7)
        trainingLog.filter { it.completed && it.date >= cutoff }.forEach { session ->
            session.exercises.forEach { exercise ->
                muscleDays.getOrPut(exercise.muscleGroup) { mutableSetOf() }.add(session.date)
            }
        }
        muscleDays.entries.filter { it.value.size > 4 }.forEach { (muscle, dates) ->
            signals.add("$muscle trained ${dates.size} days this week — add rest before next session")
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

    fun calcStreaks(
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        targets: NutritionTargets
    ): StreakResult {
        var currentStreak = 0
        val todayStr = getCurrentDate()
        val yesterdayStr = getPreviousDate(todayStr) ?: ""
        
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
        var iterations = 0
        while (iterations < 1000) {
            val dayTotals = dailyNutritionMap[tempDate]
            if (dayTotals != null &&
                dayTotals.second >= targets.protein * 0.9 &&
                Math.abs(dayTotals.first - targets.calories).toDouble() / targets.calories <= 0.15) {
                currentStreak++
                val nextDate = getPreviousDate(tempDate)
                if (nextDate == null) break
                if (nextDate == tempDate) {
                    break
                }
                tempDate = nextDate
                iterations++
            } else {
                break
            }
        }

        // Training Streak Implementation: 1 rest day per rolling 7-day window allowed
        val completedDates = trainingLog.filter { it.completed }.map { it.date }.toSet()
        var trainingStreak = 0
        
        if (completedDates.isNotEmpty()) {
            val today = getCurrentDate()
            val yesterday = getPreviousDate(today) ?: ""
            val sortedDates = completedDates.toList().sortedDescending()
            val mostRecent = sortedDates.first()
            
            // Streak is active if we trained today or yesterday
            if (mostRecent == today || mostRecent == yesterday) {
                var currentDate = mostRecent
                var restDaysInWindow = 0
                val windowSize = 7
                val history = mutableListOf<Boolean>() // true = trained, false = rest
                
                // Initial backward crawl to establish streak
                while (true) {
                    val trained = completedDates.contains(currentDate)
                    if (trained) {
                        trainingStreak++
                        history.add(true)
                    } else {
                        // Check if we can afford a rest day: 
                        // At most 1 rest day in any rolling 7-day window
                        val recentRestDays = history.takeLast(windowSize - 1).count { !it }
                        if (recentRestDays == 0) {
                            trainingStreak++
                            history.add(false)
                        } else {
                            break // Streak broken
                        }
                    }
                    val nextDate = getPreviousDate(currentDate)
                    if (nextDate == null) break
                    if (nextDate == currentDate) break
                    currentDate = nextDate
                    if (history.size > 1000) break // Safety break
                }
                
                // If the streak ends on a rest day, trim it
                while (history.isNotEmpty() && !history.last()) {
                    history.removeAt(history.size - 1)
                    trainingStreak--
                }
            }
        }
        
        return StreakResult(
            nutrition = StreakInfo(currentStreak),
            training = StreakInfo(trainingStreak)
        )
    }

    fun checkPersonalRecords(log: List<RichTrainingSession>, exerciseId: String): PRResult {
        val exerciseSessions = log.filter { it.completed }
            .flatMap { session ->
                session.exercises.filter { it.id == exerciseId }.map { log -> Pair(session.date, log) }
            }
            .sortedBy { it.first }

        if (exerciseSessions.isEmpty()) {
            return PRResult(hasPR = false)
        }

        val lastSession = exerciseSessions.last()
        val prevSessions = exerciseSessions.dropLast(1)

        val lastMaxWeight = lastSession.second.sets.filter { it.completed && !it.isWarmup }.maxOfOrNull { it.weight } ?: 0.0
        val prevMaxWeight = prevSessions.flatMap { it.second.sets }.filter { it.completed && !it.isWarmup }.maxOfOrNull { it.weight } ?: 0.0

        val hasNewWeightPR = lastMaxWeight > 0.0 && (prevSessions.isEmpty() || lastMaxWeight > prevMaxWeight)

        val newPRs = mutableListOf<PREntry>()
        if (hasNewWeightPR) {
            newPRs.add(PREntry(type = "weight", label = "Weight PR", value = "$lastMaxWeight kg", previous = if (prevMaxWeight > 0.0) "$prevMaxWeight kg" else "None"))
        }

        return PRResult(
            hasPR = hasNewWeightPR,
            newPRs = newPRs,
            exerciseId = exerciseId,
            type = if (hasNewWeightPR) "weight" else "",
            previousValue = prevMaxWeight,
            newValue = lastMaxWeight
        )
    }
}

