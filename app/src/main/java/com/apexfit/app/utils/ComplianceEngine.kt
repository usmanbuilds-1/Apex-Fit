package com.apexfit.app.utils

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

object ComplianceEngine {
    private fun confirmedDates(log: List<NutritionEntry>, confirmations: Map<String, String>): Set<String> =
        NutritionDayEvidence.snapshots(log).filter { (date, fingerprint) ->
            confirmations[date] == fingerprint
        }.keys

    private fun validTrainingDates(log: List<TrainingSession>, today: LocalDate): Set<LocalDate> =
        log.mapNotNull { session ->
            val date = runCatching { LocalDate.parse(session.date) }.getOrNull()
            if (date != null && date <= today && session.completed &&
                (session.completedAt ?: session.startedAt ?: 0L) <= System.currentTimeMillis() &&
                session.exercises.any { ex -> ex.sets.any {
                    it.completed && !it.isWarmup && it.reps > 0 && it.weight.isFinite() && it.weight >= 0.0
                } }) date else null
        }.toSet()

    /**
     * Compare confirmed, finished food-log days with the CURRENT targets.
     * There is no historical target ledger, so this must not be called historical adherence.
     * Coverage is separate from target comparison; unconfirmed days are unknown.
     */
    fun calcComplianceScores(
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        targets: NutritionTargets,
        days: Int = 14,
        nutritionConfirmations: Map<String, String> = emptyMap(),
        today: LocalDate = LocalDate.parse(getCurrentDate())
    ): ComplianceResult {
        val end = today.minusDays(1)
        val start = today.minusDays(days.coerceIn(1, 90).toLong())
        val trainingDates = validTrainingDates(trainingLog, today).filter { it in start..end }
        val observedNutrition = nutritionLog.filter {
            val date = runCatching { LocalDate.parse(it.date) }.getOrNull()
            date != null && date in start..end
        }.groupBy { it.date }
        val observedDates = observedNutrition.keys.map(LocalDate::parse) + trainingDates
        val first = observedDates.minOrNull() ?: return ComplianceResult(0, 0, 0, 0, null)
        val evaluated = (ChronoUnit.DAYS.between(first, end) + 1).toInt()
        val confirmed = confirmedDates(nutritionLog, nutritionConfirmations)
        val comparable = observedNutrition.filterKeys { it in confirmed }
        val count = comparable.size
        val coverage = (count * 100.0 / evaluated).roundToInt()
        val validTargets = targets.calories > 0 && targets.protein > 0
        fun calorieHit(entries: List<NutritionEntry>): Boolean =
            kotlin.math.abs(entries.sumOf { it.calories.toLong() } - targets.calories).toDouble() <= targets.calories * 0.10
        fun proteinHit(entries: List<NutritionEntry>): Boolean =
            entries.sumOf { it.protein } >= targets.protein
        fun percent(hits: Int, total: Int): Int = if (total == 0) 0 else (hits * 100.0 / total).roundToInt()
        val cal = if (validTargets) percent(comparable.values.count(::calorieHit), count) else 0
        val protein = if (validTargets) percent(comparable.values.count(::proteinHit), count) else 0
        // This is a comparison to current frequency, not scheduled-day adherence.
        val expected = (evaluated * targets.weeklyTrainingSessions.coerceIn(0, 7) / 7.0).roundToInt()
        val training = if (expected > 0) percent(trainingDates.size, expected).coerceAtMost(100) else 0
        val available = buildList {
            if (validTargets && count > 0) { add(cal to 0.35); add(protein to 0.40) }
            if (expected > 0) add(training to 0.25)
        }
        val overall = if (available.isEmpty()) 0 else
            (available.sumOf { it.first * it.second } / available.sumOf { it.second }).roundToInt()
        val weakestDay = if (validTargets && count >= 2) {
            comparable.entries.groupBy { LocalDate.parse(it.key).dayOfWeek }
                .mapValues { (_, rows) -> percent(rows.count { calorieHit(it.value) && proteinHit(it.value) }, rows.size) }
                .filterValues { it < 100 }.entries.minWithOrNull(compareBy({ it.value }, { it.key.value }))
                ?.key?.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
        } else null
        return ComplianceResult(cal, protein, training, overall, weakestDay,
            confirmedNutritionDays = count, evaluatedDays = evaluated, nutritionCoverage = coverage,
            nutritionComparisonAvailable = validTargets && count > 0, trainingComparisonAvailable = expected > 0)
    }

    // A nutrition streak now measures confirmed logging, not historical target adherence.
    fun calcStreaks(
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        targets: NutritionTargets,
        nutritionConfirmations: Map<String, String> = emptyMap(),
        today: LocalDate = LocalDate.parse(getCurrentDate())
    ): StreakResult {
        val confirmed = confirmedDates(nutritionLog, nutritionConfirmations)
        var cursor = today.minusDays(1)
        var current = 0
        while (cursor.toString() in confirmed) {
            current++
            cursor = cursor.minusDays(1)
        }
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val trainingDays = validTrainingDates(trainingLog, today).count { it in weekStart..today }
        return StreakResult(StreakInfo(current), StreakInfo(trainingDays))
    }
}
