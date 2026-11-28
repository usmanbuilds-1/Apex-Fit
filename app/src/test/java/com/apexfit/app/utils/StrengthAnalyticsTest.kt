package com.apexfit.app.utils

import com.apexfit.app.data.AnalyticsWorkingSet
import com.apexfit.app.data.ExerciseSet
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class StrengthAnalyticsTest {
    private val today = LocalDate.parse("2026-11-16")
    private fun row(id: Long, load: Double, reps: Int, session: String = "a",
                    date: String = today.toString(), unit: String = "kg",
                    complete: Boolean = true, status: String = "completed",
                    warmup: Boolean = false, exercise: String = "press") = AnalyticsWorkingSet(
        ExerciseSet(id = id, sessionId = session, exerciseId = exercise, exerciseName = "Press",
            muscleGroup = "Chest", weight = load, reps = reps, rpe = 8,
            isWarmup = warmup, completed = true, weightUnit = unit),
        date, "Push", complete, status, null, null, emptyList()
    )
    private fun calculate(vararg rows: AnalyticsWorkingSet) =
        StrengthAnalytics.calculate(rows.toList(), "press", today.minusYears(2), today)

    @Test fun actualLoadAndRepPairsNeverCombineDifferentSets() {
        val result = calculate(row(1, 60.0, 5), row(2, 50.0, 10)).single()
        assertEquals(60.0, result.heaviestRecorded!!.loadKg!!, 0.0)
        assertEquals(5, result.heaviestRecorded!!.reps)
        assertEquals(50.0, result.mostReps!!.loadKg!!, 0.0)
        assertEquals(10, result.mostReps!!.reps)
        assertEquals(15L, result.totalReps)
    }

    @Test fun savedPartialAndSameDaySessionsRemainSeparate() {
        val result = calculate(row(1, 60.0, 5), row(2, 50.0, 10, session = "b",
            complete = false, status = "partial"))
        assertEquals(2, result.size)
        assertTrue(result.last().partial)
    }

    @Test fun filtersInvalidFutureWarmupsAndOtherExercises() {
        val valid = row(1, 0.0, 8)
        val result = calculate(valid, valid, row(2, Double.NaN, 8), row(3, -1.0, 8),
            row(4, 20.0, 0), row(5, 50.0, 5, warmup = true),
            row(6, 50.0, 5, date = today.plusDays(1).toString()),
            row(7, 50.0, 5, complete = false, status = "cancelled"),
            row(8, 50.0, 5, exercise = "different"),
            row(9, 50.0, 5).let { it.copy(set = it.set.copy(completed = false)) })
        assertEquals(1, result.single().workingSets)
        assertEquals(0.0, result.single().heaviestRecorded!!.loadKg!!, 0.0)
    }

    @Test fun convertsLegacyPoundsOnceAndPreservesUnknownUnitReps() {
        val result = calculate(row(1, 20.0 * AppConstants.KG_TO_LBS, 5, unit = "lbs"),
            row(2, 999.0, 12, unit = "unknown")).single()
        assertEquals(20.0, result.heaviestRecorded!!.loadKg!!, 1e-8)
        assertEquals(12, result.mostReps!!.reps)
        assertNull(result.mostReps!!.loadKg)
    }

    @Test fun historicalSetAndFutureTimestampHandling() {
        val old = row(1, 20.0, 5, date = today.minusDays(400).toString())
        val future = row(2, 50.0, 5).let { it.copy(set = it.set.copy(setCompletedAt = Long.MAX_VALUE)) }
        assertEquals(today.minusDays(400), calculate(old, future).single().date)
    }
}
