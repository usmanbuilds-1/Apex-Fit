package com.apexfit.app.utils

import com.apexfit.app.data.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class HistoricalStrengthWeight(val date: LocalDate, val kg: Double, val ageDays: Long)
data class EstimatedStrengthPoint(
    val date: LocalDate, val sessionId: String, val setId: Long, val setup: String,
    val loadKg: Double, val reps: Int, val estimated1RmKg: Double,
    val bodyWeight: HistoricalStrengthWeight?,
    val trainingFormat: TrainingFormat = TrainingFormat.UNKNOWN
) {
    val relativeStrength: Double? get() = bodyWeight?.let { estimated1RmKg / it.kg }
}

object EstimatedStrength {
    // Conservative product eligibility, not a validated universal error bound.
    const val MAX_WEIGHT_AGE_DAYS = 7L

    fun epley(loadKg: Double, reps: Int): Double? {
        if (!loadKg.isFinite() || loadKg <= 0.0 || reps !in 2..5) return null
        return (loadKg * (1.0 + reps / 30.0)).takeIf { it.isFinite() }
    }

    // Strictly prior dates: historical timezone and within-day sequence are not guaranteed.
    // Equal-weight daily averages; never use the current profile weight or a future weigh-in.
    private fun dailyWeights(weights: List<WeightEntry>): List<Pair<LocalDate, Double>> =
        weights.mapNotNull { w ->
            val date = runCatching { LocalDate.parse(w.date) }.getOrNull() ?: return@mapNotNull null
            val kg = when (w.unit.trim().lowercase(java.util.Locale.ROOT)) {
                "kg" -> w.weight
                "lb", "lbs" -> w.weight / AppConstants.KG_TO_LBS
                else -> return@mapNotNull null
            }
            if (!kg.isFinite() || kg !in AppConstants.MIN_WEIGHT_KG..AppConstants.MAX_WEIGHT_KG)
                return@mapNotNull null
            date to kg
        }.groupBy({ it.first }, { it.second }).map { (date, values) -> date to values.average() }.sortedBy { it.first }

    fun calculate(points: List<StrengthSessionPoint>, confirmations: Map<Long, SetLoadConfirmation>,
                  weights: List<WeightEntry>): List<EstimatedStrengthPoint> {
        val days = dailyWeights(weights)
        return points.flatMap { point ->
            val historical = days.lastOrNull { it.first < point.date }?.let { (date, kg) ->
                val age = ChronoUnit.DAYS.between(date, point.date)
                if (age in 1..MAX_WEIGHT_AGE_DAYS) HistoricalStrengthWeight(date, kg, age) else null
            }
            point.observations.mapNotNull { observation ->
                val c = LoadConfirmations.match(observation, confirmations) ?: return@mapNotNull null
                if (observation.setId <= 0 || c.basis != RecordedLoadBasis.TOTAL || !c.freeWeightRepLimit)
                    return@mapNotNull null
                val load = observation.loadKg ?: return@mapNotNull null
                val estimate = epley(load, observation.reps) ?: return@mapNotNull null
                EstimatedStrengthPoint(point.date, point.sessionId, observation.setId,
                    LoadConfirmations.normalizedSetup(c.setup), load, observation.reps, estimate, historical, point.trainingFormat)
            }
        }.sortedWith(compareBy({ it.date }, { it.sessionId }, { it.setId }))
    }
}
