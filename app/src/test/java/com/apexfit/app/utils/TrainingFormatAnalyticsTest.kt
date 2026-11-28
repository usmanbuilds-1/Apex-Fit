package com.apexfit.app.utils

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TrainingFormatAnalyticsTest {
    private fun point(id: Long, kg: Double, format: TrainingFormat) =
        StrengthSessionPoint("s$id", LocalDate.parse("2026-11-16").plusDays(id), false,
            listOf(StrengthObservation(id, kg, 5, "fp$id", id * 1000)),
            trainingFormat = format)

    @Test fun recordsDoNotCompeteAcrossFormats() {
        val points = listOf(point(1, 60.0, TrainingFormat.SEQUENTIAL),
            point(2, 50.0, TrainingFormat.GROUPED), point(3, 40.0, TrainingFormat.UNKNOWN),
            point(4, 55.0, TrainingFormat.GROUPED), point(5, 58.0, TrainingFormat.SEQUENTIAL))
        val confirmations = (1L..5L).associateWith {
            SetLoadConfirmation(it, "fp$it", RecordedLoadBasis.TOTAL, "Gym A", true)
        }
        val records = RepRecords.calculate(points, confirmations)
        assertEquals(setOf(1L, 2L, 3L, 4L), records.events.map { it.setId }.toSet())
        assertEquals(mapOf(TrainingFormat.SEQUENTIAL to 60.0, TrainingFormat.GROUPED to 55.0,
            TrainingFormat.UNKNOWN to 40.0), records.best.associate { it.key.trainingFormat to it.loadKg })
        assertEquals(5, points.sumOf { it.workingSets })
        assertEquals(25L, points.sumOf { it.totalReps })
    }

    @Test fun strengthEstimatesPreserveFormatWithoutChangingFormula() {
        val points = TrainingFormat.values().mapIndexed { index, format -> point(index + 1L, 60.0, format) }
        val confirmations = (1L..3L).associateWith {
            SetLoadConfirmation(it, "fp$it", RecordedLoadBasis.TOTAL, "Gym A", true)
        }
        val estimates = EstimatedStrength.calculate(points, confirmations, emptyList())
        assertEquals(TrainingFormat.values().toSet(), estimates.map { it.trainingFormat }.toSet())
        assertTrue(estimates.all { kotlin.math.abs(it.estimated1RmKg - 70.0) < 1e-8 })
        assertTrue(estimates.all { it.relativeStrength == null })
    }

    @Test fun unknownTrainingFormatIsNeverInferred() {
        assertEquals(TrainingFormat.UNKNOWN, TrainingFormat.fromStored(null))
        assertEquals(TrainingFormat.UNKNOWN, TrainingFormat.fromStored("unexpected"))
        assertEquals(TrainingFormat.SEQUENTIAL, TrainingFormat.fromStored("sequential"))
        assertEquals(TrainingFormat.GROUPED, TrainingFormat.fromStored("grouped"))
    }
}
