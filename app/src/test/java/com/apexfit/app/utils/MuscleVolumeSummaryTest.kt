package com.apexfit.app.utils

import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.RichTrainingSession
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MuscleVolumeSummaryTest {
    private val today = LocalDate.parse("2026-11-16")

    @Test
    fun directWorkingSetsIncludeBodyweightAndDoNotIncludeSecondaryOrWarmups() {
        val exercise = ExerciseLog("pull-up", "Pull-up", "Lats", listOf(
            ExerciseSet(weight = 0.0, reps = 8),
            ExerciseSet(weight = 0.0, reps = 8, isWarmup = true),
            ExerciseSet(weight = 0.0, reps = 8, completed = false)
        ), secondaryMuscles = listOf("Biceps", "Biceps", "Back"))
        val current = RichTrainingSession("2026-11-16", "Pull", true, exercises = listOf(exercise))
        val rows = MuscleVolumeSummary.calculate(listOf(current), today, MuscleVolumePeriod.WEEK)
        assertEquals(listOf("back", "biceps"), rows.map { it.muscle })
        assertEquals(1, rows.first().directSets)
        assertEquals(0, rows.first().secondarySets)
        assertEquals(0, rows.last().directSets)
        assertEquals(1, rows.last().secondarySets)
    }

    @Test
    fun calendarWindowsExcludeIncompleteFutureAndOutOfPeriodSessions() {
        val exercise = ExerciseLog("squat", "Squat", "Quads", listOf(ExerciseSet(weight = 10.0, reps = 5)))
        val sessions = listOf(
            "2026-10-31" to true, "2026-11-15" to true, "2026-11-16" to true,
            "2026-11-16" to false, "2026-11-17" to true
        ).map { (date, complete) -> RichTrainingSession(date, "Legs", complete, exercises = listOf(exercise)) }
        assertEquals(1, MuscleVolumeSummary.calculate(sessions, today, MuscleVolumePeriod.WEEK).single().directSets)
        assertEquals(2, MuscleVolumeSummary.calculate(sessions, today, MuscleVolumePeriod.MONTH).single().directSets)
    }

    @Test
    fun sameNamedExercisesKeepDistinctStableKeys() {
        val entries = listOf("custom-a", "custom-b").map { id ->
            ExerciseLog(id, "Cable Row", "Back", listOf(ExerciseSet(weight = 20.0, reps = 10)))
        }
        val session = RichTrainingSession("2026-11-16", "Pull", true, exercises = entries)

        val row = MuscleVolumeSummary.calculate(listOf(session), today, MuscleVolumePeriod.WEEK).single()

        assertEquals(2, row.directSets)
        assertEquals(setOf("custom-a", "custom-b"), row.exercises.map { it.key }.toSet())
    }
}
