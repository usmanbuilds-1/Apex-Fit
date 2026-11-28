package com.apexfit.app.utils

import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.RichTrainingSession
import org.junit.Assert.*
import org.junit.Test

class FatigueEngineTest {

    // ── Helpers ───────────────────────────────────────────────

    private fun session(
        daysAgo: Int,
        sets: Int = 3,
        avgWeightKg: Double = 60.0,
        rpe: Int = 8,
        sessionFeel: Int = 3,
        completed: Boolean = true
    ): TrainingSession {
        val workingSets = (1..sets).map {
            ExerciseSet(weight = avgWeightKg, reps = 8, rpe = rpe, isWarmup = false, completed = true)
        }
        return RichTrainingSession(
            date = getDateDaysAgo(daysAgo),
            sessionType = "UPPER",
            completed = completed,
            sessionFeel = sessionFeel,
            exercises = listOf(ExerciseLog("ex1", "Squat", "quads", workingSets))
        )
    }

    // Creates a balanced training history: 3 sessions/week for N weeks
    // Acute (last 6 days) = 3 sessions; chronic weekly avg = 3 sessions → ratio ≈ 1.0
    private fun balancedHistory(): List<TrainingSession> = listOf(
        // Acute window (last 6 days)
        session(daysAgo = 1), session(daysAgo = 3), session(daysAgo = 5),
        // Week 2
        session(daysAgo = 8), session(daysAgo = 10), session(daysAgo = 12),
        // Week 3
        session(daysAgo = 15), session(daysAgo = 17), session(daysAgo = 19),
        // Week 4
        session(daysAgo = 22), session(daysAgo = 24), session(daysAgo = 26)
    )

    // ── Insufficient data ─────────────────────────────────────

    @Test
    fun insufficientData_emptyLog_returnsNullRatio() {
        val result = FatigueEngine.calcFatigueToFitnessRatio(emptyList())
        assertNull(result.ratio)
        assertEquals("unknown", result.status)
    }

    @Test
    fun insufficientData_fewerThan7Sessions_returnsNullRatio() {
        val sessions = (1..5).map { session(daysAgo = it * 4) }
        val result = FatigueEngine.calcFatigueToFitnessRatio(sessions)
        assertNull(result.ratio)
        assertEquals("unknown", result.status)
    }

    @Test
    fun insufficientData_noPreAcuteWorkouts_returnsNullRatio() {
        // All sessions are within the last 5 days — no chronic baseline
        val sessions = (1..8).map { session(daysAgo = (it % 5) + 1) }
        val result = FatigueEngine.calcFatigueToFitnessRatio(sessions)
        assertNull(result.ratio)
    }

    // ── Status thresholds ─────────────────────────────────────

    @Test
    fun balancedTraining_returnsOptimalStatus() {
        // Equal acute and chronic load → ratio ≈ 1.0 → "optimal"
        val result = FatigueEngine.calcFatigueToFitnessRatio(balancedHistory())
        assertEquals("optimal", result.status)
        assertNotNull(result.ratio)
        assertTrue("Ratio ${result.ratio} should be in optimal range 0.8–1.3",
            result.ratio!! in 0.8..1.3)
    }

    @Test
    fun spikedAcuteLoad_returnsHighOrDangerStatus() {
        // 6 sessions in the last 5 days on top of normal chronic baseline
        val history = balancedHistory().toMutableList()
        history.addAll(listOf(
            session(daysAgo = 0), session(daysAgo = 2),
            session(daysAgo = 4)
        ))
        val result = FatigueEngine.calcFatigueToFitnessRatio(history)
        assertNotNull(result.ratio)
        assertTrue("Spiked ratio ${result.ratio} should be > 1.3",
            result.ratio!! > 1.3)
        assertTrue("Status should be high or danger, got ${result.status}",
            result.status == "high" || result.status == "danger")
    }

    @Test
    fun noRecentTraining_returnsDetrainingOrLowStatus() {
        // Good chronic history but nothing in the last 10 days
        val history = listOf(
            session(daysAgo = 10), session(daysAgo = 12), session(daysAgo = 14),
            session(daysAgo = 17), session(daysAgo = 19), session(daysAgo = 21),
            session(daysAgo = 24), session(daysAgo = 26)
        )
        val result = FatigueEngine.calcFatigueToFitnessRatio(history)
        assertNotNull(result.ratio)
        assertTrue("Status should be detraining or low, got ${result.status}",
            result.status == "detraining" || result.status == "low")
    }

    // ── Load calculation factors ──────────────────────────────

    @Test
    fun absoluteWeightAndSessionFeelDoNotChangeLoggedSetRatio() {
        val original = balancedHistory()
        val changed = original.map { session ->
            session.copy(sessionFeel = 1, exercises = session.exercises.map { exercise ->
                exercise.copy(sets = exercise.sets.map { it.copy(weight = 0.0, rpe = 10) })
            })
        }
        val expected = FatigueEngine.calcFatigueToFitnessRatio(original)
        val actual = FatigueEngine.calcFatigueToFitnessRatio(changed)
        assertEquals(1.0, expected.ratio!!, 0.001)
        assertEquals(expected, actual)
    }

    @Test
    fun missingBaselineWeekSuppressesRatioEvenWithManyRecentWorkouts() {
        val history = balancedHistory().filter { it.date >= getDateDaysAgo(20) }
        assertNull(FatigueEngine.calcFatigueToFitnessRatio(history).ratio)
    }

    @Test
    fun duplicatePersistedSetsAndInvalidOrFutureSetsDoNotInflateVolume() {
        val base = session(0)
        val exercise = base.exercises.single()
        val valid = exercise.sets.first().copy(id = 1L, weight = 0.0)
        val other = valid.copy(id = 2L)
        val modified = base.copy(exercises = listOf(exercise.copy(sets = listOf(
            valid, valid, other, other.copy(id = 3L, reps = 0),
            other.copy(id = 4L, weight = Double.NaN), other.copy(id = 5L, isWarmup = true)
        ))))
        val future = modified.copy(date = java.time.LocalDate.parse(getCurrentDate()).plusDays(1).toString())
        val history = listOf(modified, modified, future)
        assertEquals(2.0, FatigueEngine.calcFatigueToFitnessRatio(history).acuteLoad, 0.0)
        assertEquals(2, FatigueEngine.calcMuscleHeatmap(history).values.sumOf { it.volume })
        assertEquals(1.5, AlgorithmEngine.calcEffectiveSets(history).values.sum(), 0.001)
        assertEquals(2, MuscleVolumeSummary.calculate(history,
            java.time.LocalDate.parse(getCurrentDate()), MuscleVolumePeriod.MONTH).sumOf { it.directSets })
    }

    // ── Output fields ─────────────────────────────────────────

    @Test
    fun result_acuteAndChronicLoad_alwaysNonNegative() {
        val result = FatigueEngine.calcFatigueToFitnessRatio(balancedHistory())
        assertTrue("Acute load should be >= 0", result.acuteLoad >= 0.0)
        assertTrue("Chronic load should be >= 0", result.chronicLoad >= 0.0)
    }

    @Test
    fun result_recommendationNeverBlank() {
        val resultEmpty = FatigueEngine.calcFatigueToFitnessRatio(emptyList())
        val resultFull  = FatigueEngine.calcFatigueToFitnessRatio(balancedHistory())
        assertTrue(resultEmpty.recommendation.isNotBlank())
        assertTrue(resultFull.recommendation.isNotBlank())
    }
}
