package com.apexfit.app.utils

import com.apexfit.app.data.WeightEntry
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EstimatedStrengthTest {
    private val date = LocalDate.parse("2026-11-16")
    private fun point(id: Long = 1, kg: Double = 60.0, reps: Int = 5, day: LocalDate = date) =
        StrengthSessionPoint("s$id", day, false, listOf(StrengthObservation(id, kg, reps, "fingerprint$id")))
    private fun confirm(id: Long = 1, basis: RecordedLoadBasis = RecordedLoadBasis.TOTAL,
                        limit: Boolean = true, setup: String = "Gym A") =
        SetLoadConfirmation(id, "fingerprint$id", basis, setup, limit)

    @Test fun epleyUsesActualRepsWithoutRirExtrapolation() {
        assertEquals(70.0, EstimatedStrength.epley(60.0, 5)!!, 1e-8)
        assertEquals(64.0, EstimatedStrength.epley(60.0, 2)!!, 1e-8)
        for (reps in listOf(0, 1, 6, 10, 30)) assertNull(EstimatedStrength.epley(60.0, reps))
        for (kg in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertNull(EstimatedStrength.epley(kg, 5))
    }

    @Test fun eligibilityRequiresMatchingConfirmedTotalFreeWeightRepLimit() {
        val points = listOf(point(1), point(2), point(3), point(4), point(5, reps = 8), point(6))
        val confirmations = listOf(confirm(1), confirm(2, RecordedLoadBasis.PER_HAND),
            confirm(3, RecordedLoadBasis.MACHINE_STACK), confirm(4, limit = false),
            confirm(5), confirm(6).copy(fingerprint = "edited")).associateBy { it.setId }
        assertEquals(listOf(1L), EstimatedStrength.calculate(points, confirmations, emptyList()).map { it.setId })
    }

    @Test fun historicalWeightUsesPriorDailyAverageAndNeverCurrentOrFuture() {
        val weights = listOf(
            WeightEntry(date = "2026-11-09", weight = 60.0),
            WeightEntry(date = "2026-11-15", weight = 69.0),
            WeightEntry(date = "2026-11-15", weight = 71.0),
            WeightEntry(date = "2026-11-16", weight = 100.0),
            WeightEntry(date = "2026-11-17", weight = 120.0))
        val result = EstimatedStrength.calculate(listOf(point()), mapOf(1L to confirm()), weights).single()
        assertEquals(70.0, result.estimated1RmKg, 1e-8)
        assertEquals(70.0, result.bodyWeight!!.kg, 1e-8)
        assertEquals(1L, result.bodyWeight!!.ageDays)
        assertEquals(1.0, result.relativeStrength!!, 1e-8)
    }

    @Test fun staleInvalidAndSameDayOnlyWeightsDoNotProduceRelativeStrength() {
        val weights = listOf(WeightEntry(date = "2026-11-08", weight = 70.0),
            WeightEntry(date = "2026-11-15", weight = Double.NaN),
            WeightEntry(date = "2026-11-16", weight = 70.0))
        val result = EstimatedStrength.calculate(listOf(point()), mapOf(1L to confirm()), weights).single()
        assertNull(result.relativeStrength)
        assertEquals(70.0, result.estimated1RmKg, 1e-8)
    }

    @Test fun sevenDayBoundaryAndPoundConversion() {
        val weights = listOf(WeightEntry(date = "2026-11-09", weight = 70.0 * AppConstants.KG_TO_LBS, unit = "lbs"))
        val result = EstimatedStrength.calculate(listOf(point()), mapOf(1L to confirm()), weights).single()
        assertEquals(7L, result.bodyWeight!!.ageDays)
        assertEquals(1.0, result.relativeStrength!!, 1e-8)
    }

    @Test fun oldConfirmationDecodesWithoutEffortAndRoundTripsNewConfirmation() {
        val old = LoadConfirmations.decode("1|${"a".repeat(64)}|TOTAL|Gym A")!!
        assertFalse(old.freeWeightRepLimit)
        val new = old.copy(freeWeightRepLimit = true)
        assertEquals(new, LoadConfirmations.decode(LoadConfirmations.encode(new)))
        assertNull(LoadConfirmations.decode(LoadConfirmations.encode(new.copy(basis = RecordedLoadBasis.MACHINE_STACK))))
    }
}
