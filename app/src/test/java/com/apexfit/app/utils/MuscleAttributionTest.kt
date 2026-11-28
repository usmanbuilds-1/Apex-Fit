package com.apexfit.app.utils

import com.apexfit.app.data.ActiveSession
import com.apexfit.app.data.ActiveExercise
import com.apexfit.app.data.ActiveSet
import com.apexfit.app.data.AnalyticsWorkingSet
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.TrainingSession
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MuscleAttributionTest {
    private val date = LocalDate.parse("2026-11-16")
    private val first = ExerciseSet(id = 1L, sessionId = "s", exerciseId = "row", exerciseName = "Row",
        muscleGroup = "Back", weight = 25.0, reps = 8, rpe = 8, isWarmup = false, completed = true,
        secondaryMuscleSnapshot = MuscleAttribution.encode("Back", listOf("Biceps")))

    @Test fun knownEmptyLegacyAndMalformedAreDistinct() {
        val known = MuscleAttribution.resolve("Back", "[]", listOf("Biceps"))
        assertEquals(MuscleAttribution.Source.CAPTURED, known.source)
        assertTrue(known.secondary.isEmpty())
        assertEquals(MuscleAttribution.Source.LEGACY_INFERRED,
            MuscleAttribution.resolve("Back", null, listOf("Biceps")).source)
        assertEquals(MuscleAttribution.Source.UNKNOWN, MuscleAttribution.resolve("Back", null, null).source)
        assertEquals(MuscleAttribution.Source.UNKNOWN, MuscleAttribution.resolve("Back", "bad", listOf("Biceps")).source)
        assertEquals(listOf("biceps"), MuscleAttribution.decode(MuscleAttribution.encode("Back", listOf("Back", "Biceps", "biceps"))))
    }

    @Test fun historicalGroupingDoesNotApplyOneSnapshotToAllSets() {
        val second = first.copy(id = 2L, secondaryMuscleSnapshot = "[]")
        val legacy = first.copy(id = 3L, secondaryMuscleSnapshot = null)
        val saved = listOf(first, second, legacy)
        fun facts(set: ExerciseSet, current: List<String>) =
            AnalyticsWorkingSet(set, date.toString(), "Pull", true, "completed", null, null, current)
        val history = AnalyticsHistory.sessions(saved.map { facts(it, listOf("Forearms")) })
        val fromMapper = SessionMapper.buildRichSessions(
            listOf(TrainingSession("s", date.toString(), "Pull", true, 20, 3)), saved,
            mapOf("row" to listOf("Forearms")))
        for (source in listOf(history, fromMapper)) {
            val rows = MuscleVolumeSummary.calculate(source, date, MuscleVolumePeriod.WEEK)
            assertEquals(3, rows.single { it.muscle == "back" }.directSets)
            assertEquals(1, rows.single { it.muscle == "biceps" }.secondarySets)
            assertEquals(1, rows.single { it.muscle == "forearms" }.secondarySets)
        }
        val revised = AnalyticsHistory.sessions(saved.map { facts(it, listOf("Triceps")) })
        val rows = MuscleVolumeSummary.calculate(revised, date, MuscleVolumePeriod.WEEK)
        assertEquals(1, rows.single { it.muscle == "biceps" }.secondarySets)
        assertFalse(rows.any { it.muscle == "forearms" })
        assertEquals(1, rows.single { it.muscle == "triceps" }.secondarySets)
    }

    @Test fun draftUpgradePreservesIdentityAndDoesNotInventSnapshot() {
        val ex = ActiveExercise("row", "Row", "Back", mutableListOf(ActiveSet(1, 25.0, 8)))
        val legacy = ActiveSession("1", "Pull", exercises = mutableListOf(ex), schemaVersion = 4)
        val decoded = ActiveWorkoutCodec.decode(Gson().toJson(legacy))
        assertTrue(decoded.migrated)
        assertEquals(legacy.sessionId, decoded.session.sessionId)
        assertNull(decoded.session.exercises.single().secondaryMuscleSnapshot)
        val captured = decoded.session.copy(exercises = mutableListOf(ex.copy(secondaryMuscleSnapshot = "[]")))
        assertEquals("[]", ActiveWorkoutCodec.decode(Gson().toJson(captured)).session.exercises.single().secondaryMuscleSnapshot)
    }
}
