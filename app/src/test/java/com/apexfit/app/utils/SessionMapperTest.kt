package com.apexfit.app.utils

import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.TrainingSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SessionMapperTest {
    @Test
    fun storedSetsAndSecondaryAttributionReachAnalyticsWithoutLosingIdentity() {
        val date = LocalDate.parse("2026-11-16")
        val session = TrainingSession("s1", date.toString(), "Pull", true, 30, 3)
        val saved = ExerciseSet(id = 42L, sessionId = "s1", exerciseId = "row",
            exerciseName = "Row", muscleGroup = "Back", weight = 25.0, reps = 8,
            rpe = 8, isWarmup = false, completed = true, occurrenceIndex = 1, setNumber = 2)
        val rich = SessionMapper.buildRichSessions(listOf(session), listOf(saved, saved),
            mapOf("row" to listOf("Biceps")))
        assertEquals(saved, rich.single().exercises.single().sets.first())
        val rows = MuscleVolumeSummary.calculate(rich, date, MuscleVolumePeriod.WEEK)
        assertEquals(1, rows.first { it.muscle == "back" }.directSets)
        assertEquals(1, rows.first { it.muscle == "biceps" }.secondarySets)
    }

    @Test
    fun recordedPrimaryMuscleIsNotOverwrittenByAnotherOccurrence() {
        val date = "2026-11-16"
        val session = TrainingSession("s1", date, "Mixed", true, 30, 3)
        val saved = ExerciseSet(id = 42L, sessionId = "s1", exerciseId = "row",
            exerciseName = "Row", muscleGroup = "Back", weight = 25.0, reps = 8,
            rpe = 8, isWarmup = false, completed = true)
        val rows = SessionMapper.buildRichSessions(listOf(session), listOf(saved,
            saved.copy(id = 43L, muscleGroup = "Shoulders")))
        assertEquals(2, rows.single().exercises.size)
    }
}
