package com.apexfit.app.utils

import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.RichTrainingSession
import org.junit.Assert.*
import org.junit.Test

class PlateauEngineTest {

    // ── Helpers ───────────────────────────────────────────────

    private fun workingSet(weight: Double, reps: Int, rpe: Int = 8): ExerciseSet =
        ExerciseSet(weight = weight, reps = reps, rpe = rpe, isWarmup = false, completed = true)

    private fun warmupSet(weight: Double, reps: Int): ExerciseSet =
        ExerciseSet(weight = weight, reps = reps, rpe = 6, isWarmup = true, completed = true)

    private fun session(
        date: String,
        exerciseName: String,
        sets: List<ExerciseSet>,
        completed: Boolean = true
    ): RichTrainingSession = RichTrainingSession(
        date = date,
        sessionType = "UPPER",
        completed = completed,
        exercises = listOf(
            ExerciseLog(id = "ex1", name = exerciseName, muscleGroup = "chest", sets = sets)
        )
    )

    // ── Insufficient data ─────────────────────────────────────

    @Test
    fun insufficientSessions_fewerThan4_returnsFalse() {
        val sessions = listOf(
            session("2025-01-01", "Bench Press", listOf(workingSet(60.0, 10), workingSet(60.0, 10))),
            session("2025-01-05", "Bench Press", listOf(workingSet(60.0, 10), workingSet(60.0, 10))),
            session("2025-01-09", "Bench Press", listOf(workingSet(60.0, 9), workingSet(60.0, 9)))
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertFalse(result.isPlateaued)
    }

    @Test
    fun emptySessionList_returnsFalse() {
        val result = PlateauEngine.detectStrengthPlateau(emptyList())
        assertFalse(result.isPlateaued)
    }

    @Test
    fun sessionsWithNoExercises_returnsFalse() {
        val sessions = (1..5).map { i ->
            RichTrainingSession(
                date = "2025-01-0$i",
                sessionType = "UPPER",
                completed = true,
                exercises = emptyList()
            )
        }
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertFalse(result.isPlateaued)
    }

    // ── Progress — no plateau ─────────────────────────────────

    @Test
    fun consistentlyImprovingE1RM_returnsFalse() {
        // e1RM grows each session: ~80, ~82.7, ~86.7, ~89.3
        val sessions = listOf(
            session("2025-01-01", "Bench Press", listOf(workingSet(60.0, 10), workingSet(60.0, 10))),
            session("2025-01-05", "Bench Press", listOf(workingSet(62.0, 10), workingSet(62.0, 10))),
            session("2025-01-09", "Bench Press", listOf(workingSet(65.0, 10), workingSet(65.0, 10))),
            session("2025-01-13", "Bench Press", listOf(workingSet(67.0, 10), workingSet(67.0, 10)))
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertFalse(result.isPlateaued)
    }

    // ── Early plateau (2 consecutive stalls) ──────────────────

    @Test
    fun twoConsecutiveNonImprovingSessions_returnsEarlyPlateau() {
        val sessions = listOf(
            session("2025-01-01", "Bench Press", listOf(workingSet(60.0, 10), workingSet(60.0, 10))),
            session("2025-01-05", "Bench Press", listOf(workingSet(62.5, 10), workingSet(62.5, 10))),
            session("2025-01-09", "Bench Press", listOf(workingSet(62.5, 10), workingSet(62.5, 9))),
            session("2025-01-13", "Bench Press", listOf(workingSet(62.5, 10), workingSet(62.5, 9)))
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertTrue(result.isPlateaued)
        assertEquals("early", result.severity)
    }

    // ── Confirmed plateau (3+ consecutive stalls) ─────────────

    @Test
    fun threeConsecutiveNonImprovingSessions_returnsConfirmedPlateau() {
        val sessions = listOf(
            session("2025-01-01", "Bench Press", listOf(workingSet(60.0, 10), workingSet(60.0, 10))),
            session("2025-01-05", "Bench Press", listOf(workingSet(62.5, 10), workingSet(62.5, 10))),
            session("2025-01-09", "Bench Press", listOf(workingSet(62.5, 10), workingSet(62.5, 9))),
            session("2025-01-13", "Bench Press", listOf(workingSet(62.5, 9),  workingSet(62.5, 9))),
            session("2025-01-17", "Bench Press", listOf(workingSet(62.5, 10), workingSet(62.5, 8)))
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertTrue(result.isPlateaued)
        assertEquals("confirmed", result.severity)
    }

    // ── Set filtering ─────────────────────────────────────────

    @Test
    fun warmupSetsIgnored_plateauStillDetected() {
        val sessions = listOf(
            session("2025-01-01", "Bench Press", listOf(
                warmupSet(30.0, 15), workingSet(62.5, 10), workingSet(62.5, 10)
            )),
            session("2025-01-05", "Bench Press", listOf(
                warmupSet(40.0, 15), workingSet(62.5, 10), workingSet(62.5, 9)
            )),
            session("2025-01-09", "Bench Press", listOf(
                warmupSet(50.0, 15), workingSet(62.5, 10), workingSet(62.5, 9)
            )),
            session("2025-01-13", "Bench Press", listOf(
                warmupSet(50.0, 15), workingSet(62.5, 9),  workingSet(62.5, 8)
            )),
            session("2025-01-17", "Bench Press", listOf(
                warmupSet(50.0, 15), workingSet(62.5, 10), workingSet(62.5, 9)
            ))
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertTrue(result.isPlateaued)
    }

    @Test
    fun rpe10SetsExcluded_returnsNoPlateauWhenOnlyMaxSets() {
        val sessions = listOf(
            session("2025-01-01", "Bench Press", listOf(workingSet(62.5, 5, rpe = 10), workingSet(62.5, 4, rpe = 10))),
            session("2025-01-05", "Bench Press", listOf(workingSet(62.5, 5, rpe = 10), workingSet(62.5, 4, rpe = 10))),
            session("2025-01-09", "Bench Press", listOf(workingSet(62.5, 5, rpe = 10), workingSet(62.5, 4, rpe = 10))),
            session("2025-01-13", "Bench Press", listOf(workingSet(62.5, 5, rpe = 10), workingSet(62.5, 4, rpe = 10)))
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertFalse(result.isPlateaued)
    }

    @Test
    fun belowWorkingRpeSetsExcluded_returnsNoPlateauWhenOnlyEasySets() {
        val sessions = listOf(
            session("2025-01-01", "Bench Press", listOf(workingSet(62.5, 10, rpe = 6), workingSet(62.5, 10, rpe = 6))),
            session("2025-01-05", "Bench Press", listOf(workingSet(62.5, 10, rpe = 6), workingSet(62.5, 10, rpe = 6))),
            session("2025-01-09", "Bench Press", listOf(workingSet(62.5, 10, rpe = 6), workingSet(62.5, 10, rpe = 6))),
            session("2025-01-13", "Bench Press", listOf(workingSet(62.5, 10, rpe = 6), workingSet(62.5, 10, rpe = 6)))
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertFalse(result.isPlateaued)
    }

    // ── Incomplete sessions ignored ───────────────────────────

    @Test
    fun incompleteSessions_ignoredInDetection() {
        // Only 3 completed sessions — below MIN_SESSIONS_FOR_DETECTION (4)
        val sessions = listOf(
            session("2025-01-01", "Bench Press", listOf(workingSet(60.0, 10), workingSet(60.0, 10)), completed = true),
            session("2025-01-05", "Bench Press", listOf(workingSet(62.5, 10), workingSet(62.5, 10)), completed = false),
            session("2025-01-09", "Bench Press", listOf(workingSet(62.5, 10), workingSet(62.5, 9)),  completed = true),
            session("2025-01-13", "Bench Press", listOf(workingSet(62.5, 9),  workingSet(62.5, 9)),  completed = false),
            session("2025-01-17", "Bench Press", listOf(workingSet(62.5, 10), workingSet(62.5, 9)),  completed = true)
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertFalse(result.isPlateaued)
    }

    // ── Multiple exercises ────────────────────────────────────

    @Test
    fun multipleExercises_returnsWorstPlateau() {
        val sessions = listOf(
            RichTrainingSession("2025-01-01", "FULL", true, exercises = listOf(
                ExerciseLog("sq", "Squat",       "legs",  listOf(workingSet(100.0, 5), workingSet(100.0, 5))),
                ExerciseLog("bp", "Bench Press",  "chest", listOf(workingSet(62.5, 10), workingSet(62.5, 10)))
            )),
            RichTrainingSession("2025-01-05", "FULL", true, exercises = listOf(
                ExerciseLog("sq", "Squat",       "legs",  listOf(workingSet(105.0, 5), workingSet(105.0, 5))),
                ExerciseLog("bp", "Bench Press",  "chest", listOf(workingSet(62.5, 10), workingSet(62.5, 9)))
            )),
            RichTrainingSession("2025-01-09", "FULL", true, exercises = listOf(
                ExerciseLog("sq", "Squat",       "legs",  listOf(workingSet(107.5, 5), workingSet(107.5, 5))),
                ExerciseLog("bp", "Bench Press",  "chest", listOf(workingSet(62.5, 9),  workingSet(62.5, 9)))
            )),
            RichTrainingSession("2025-01-13", "FULL", true, exercises = listOf(
                ExerciseLog("sq", "Squat",       "legs",  listOf(workingSet(110.0, 5), workingSet(110.0, 5))),
                ExerciseLog("bp", "Bench Press",  "chest", listOf(workingSet(62.5, 10), workingSet(62.5, 8)))
            )),
            RichTrainingSession("2025-01-17", "FULL", true, exercises = listOf(
                ExerciseLog("sq", "Squat",       "legs",  listOf(workingSet(112.5, 5), workingSet(112.5, 5))),
                ExerciseLog("bp", "Bench Press",  "chest", listOf(workingSet(62.5, 9),  workingSet(62.5, 8)))
            ))
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertTrue(result.isPlateaued)
        assertTrue(result.interventions.any { it.contains("Bench Press") })
    }

    // ── Output fields ─────────────────────────────────────────

    @Test
    fun daysStalled_isPositiveWhenPlateauDetected() {
        val sessions = listOf(
            session("2025-01-01", "Bench Press", listOf(workingSet(60.0, 10),  workingSet(60.0, 10))),
            session("2025-01-05", "Bench Press", listOf(workingSet(62.5, 10),  workingSet(62.5, 10))),
            session("2025-01-09", "Bench Press", listOf(workingSet(62.5, 10),  workingSet(62.5, 9))),
            session("2025-01-13", "Bench Press", listOf(workingSet(62.5, 9),   workingSet(62.5, 9))),
            session("2025-01-17", "Bench Press", listOf(workingSet(62.5, 10),  workingSet(62.5, 8)))
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertTrue(result.isPlateaued)
        assertTrue(result.daysStalled > 0)
    }

    @Test
    fun confirmedPlateau_interventionsNotEmpty() {
        val sessions = listOf(
            session("2025-01-01", "Bench Press", listOf(workingSet(60.0, 10),  workingSet(60.0, 10))),
            session("2025-01-05", "Bench Press", listOf(workingSet(62.5, 10),  workingSet(62.5, 10))),
            session("2025-01-09", "Bench Press", listOf(workingSet(62.5, 10),  workingSet(62.5, 9))),
            session("2025-01-13", "Bench Press", listOf(workingSet(62.5, 9),   workingSet(62.5, 9))),
            session("2025-01-17", "Bench Press", listOf(workingSet(62.5, 10),  workingSet(62.5, 8)))
        )
        val result = PlateauEngine.detectStrengthPlateau(sessions)
        assertTrue(result.isPlateaued)
        assertTrue(result.interventions.isNotEmpty())
        assertTrue(result.interventionRecommendation.isNotBlank())
    }
}
