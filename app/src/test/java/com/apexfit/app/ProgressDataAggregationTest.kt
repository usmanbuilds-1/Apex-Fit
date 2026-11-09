package com.apexfit.app

import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProgressDataAggregationTest {
    private fun session(id: String, date: String) = TrainingSession(
        id = id,
        date = date,
        sessionType = "Upper",
        completed = true,
        durationMinutes = 50,
        sessionFeel = 4
    )

    private fun set(
        sessionId: String,
        muscle: String,
        completed: Boolean = true,
        warmup: Boolean = false
    ) = ExerciseSet(
        sessionId = sessionId,
        exerciseId = "test-exercise",
        exerciseName = "Test exercise",
        muscleGroup = muscle,
        weight = 10.0,
        reps = 8,
        rpe = 8,
        isWarmup = warmup,
        completed = completed
    )

    @Test fun monthlyVolumeCountsEachCompletedWorkingSetOnce() {
        val sessions = listOf(
            session("nov", "2026-11-02"),
            session("oct", "2026-10-30")
        )
        val sets = listOf(
            set("nov", " CHEST "),
            set("nov", "chest"),
            set("nov", "Back"),
            set("nov", "Chest", warmup = true),
            set("nov", "Chest", completed = false),
            set("oct", "Chest"),
            set("not-a-session", "Chest")
        )
        val result = ProgressDataAggregation.monthlyWorkingSets(sessions, sets, "2026-11")

        assertEquals(2, result["Chest"])
        assertEquals(1, result["Back"])
        assertEquals(0, result["Quads"])
        assertEquals(21, result.size)
    }

    @Test fun latestDateIgnoresWarmupsIncompleteSetsAndMissingSessions() {
        val sessions = listOf(
            session("early", "2026-10-10"),
            session("late", "2026-11-03"),
            session("old", "2026-09-01")
        )
        val sets = listOf(
            set("early", "Chest"),
            set("late", "Chest"),
            set("late", "Upper Back"),
            set("late", "Quads", warmup = true),
            set("old", "Quads"),
            set("not-a-session", "Calves"),
            set("late", "Calves", completed = false)
        )
        val latest = ProgressDataAggregation.latestSessionDates(
            sessions, sets, listOf("Chest", "Back", "Quads", "Calves")
        ) { group, muscle ->
            group.equals(muscle, ignoreCase = true) ||
                (group == "Upper Back" && muscle == "Back")
        }

        assertEquals("2026-11-03", latest["Chest"])
        assertEquals("2026-11-03", latest["Back"])
        assertEquals("2026-09-01", latest["Quads"])
        assertFalse(latest.containsKey("Calves"))
    }
}
