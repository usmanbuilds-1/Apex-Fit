package com.apexfit.app.ui.screens

import com.apexfit.app.ui.models.UiExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutReceiptSummaryTest {
    @Test
    fun completedExerciseSummaryKeepsPerSetLoadsAndRepsAndSumsRecordedRest() {
        val sets = listOf(
            UiExerciseSet(weight = 15.0, reps = 12, rpe = 8, exerciseId = "press", exerciseName = "Dumbbell Chest Press", restTaken = 90),
            UiExerciseSet(weight = 15.0, reps = 13, rpe = 8, exerciseId = "press", exerciseName = "Dumbbell Chest Press", restTaken = 75),
            UiExerciseSet(weight = 12.5, reps = 12, rpe = 9, exerciseId = "press", exerciseName = "Dumbbell Chest Press", restTaken = 0),
            UiExerciseSet(weight = 20.0, reps = 8, rpe = 6, isWarmup = true, exerciseId = "press", exerciseName = "Dumbbell Chest Press", restTaken = 30),
            UiExerciseSet(weight = 100.0, reps = 10, rpe = 9, completed = false, exerciseId = "press", exerciseName = "Dumbbell Chest Press", restTaken = 300)
        )

        val summary = summarizeCompletedExercises(sets).single()

        assertEquals("Dumbbell Chest Press", summary.exerciseName)
        assertEquals(listOf(12, 13, 12), summary.workingSets.map { it.reps })
        assertEquals(listOf(15.0, 15.0, 12.5), summary.workingSets.map { it.weight })
        assertEquals(1, summary.warmupSets.size)
        assertEquals(195L, summary.actualRestSeconds)
        assertEquals(195L, totalActualRestSeconds(sets))
    }

    @Test
    fun durationUsesMinutesAndAddsHoursWhenNeeded() {
        assertEquals("12:40", formatReceiptDuration(760))
        assertEquals("1:01:05", formatReceiptDuration(3_665))
        assertEquals("0:00", formatReceiptDuration(-10))
    }

    @Test
    fun uncompletedExerciseSetsDoNotAppearInReceipt() {
        val summaries = summarizeCompletedExercises(
            listOf(
                UiExerciseSet(weight = 20.0, reps = 8, rpe = 0, completed = false, exerciseId = "row", exerciseName = "Row")
            )
        )

        assertTrue(summaries.isEmpty())
        assertEquals(0L, totalActualRestSeconds(emptyList()))
    }

    @Test
    fun repeatedCatalogExerciseKeepsSeparateRecordedOccurrences() {
        val first = UiExerciseSet(weight = 40.0, reps = 5, rpe = 8, exerciseId = "press",
            exerciseName = "Press", occurrenceIndex = 0, setNumber = 1, restTaken = 91)
        val second = first.copy(weight = 20.0, reps = 11, occurrenceIndex = 1, restTaken = 37)
        val summaries = summarizeCompletedExercises(listOf(first, second))
        assertEquals(2, summaries.size)
        assertEquals(listOf(40.0, 20.0), summaries.map { it.workingSets.single().weight })
        assertEquals(listOf(91L, 37L), summaries.map { it.actualRestSeconds })
        val legacy = summarizeCompletedExercises(listOf(first.copy(occurrenceIndex = null), second.copy(occurrenceIndex = null)))
        assertEquals(1, legacy.size)
        assertEquals(2, legacy.single().workingSets.size)
    }

    @Test
    fun advancedBoutCountsOneSetButIncludesContinuationRepsAndRest() {
        val details = com.apexfit.app.utils.AdvancedSetDetails(
            com.apexfit.app.utils.AdvancedSetKind.DROP,
            com.apexfit.app.utils.SetOutcome.UNKNOWN,
            listOf(com.apexfit.app.utils.SetContinuation(15.0, 6, 5, com.apexfit.app.utils.SetOutcome.UNKNOWN),
                com.apexfit.app.utils.SetContinuation(10.0, 5, 10, com.apexfit.app.utils.SetOutcome.UNKNOWN))
        )
        val set = UiExerciseSet(weight = 20.0, reps = 10, rpe = 0, exerciseId = "curl",
            restTaken = 90, advancedDetails = com.apexfit.app.utils.AdvancedSets.encode(20.0, details))
        val sets = listOf(set, set.copy(isWarmup = true, advancedDetails = null, restTaken = 0),
            set.copy(completed = false))
        assertEquals(1, summarizeCompletedExercises(sets).single().workingSets.size)
        assertEquals(21L, receiptWorkingReps(sets))
        assertEquals(105L, totalActualRestSeconds(sets))
    }
}
