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
            exercises = listOf(ExerciseLog("ex1", "Squat", "legs", workingSets))
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
        val sessions = (1..8).map { session(daysAgo = it) }
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
    fun heavierWeight_producesHigherLoadThanLighterWeight() {
        // Two identical training histories, one at 60kg and one at 90kg
        // The heavier one should produce a higher ratio (more acute relative to same chronic)
        fun historyAtWeight(kg: Double): List<TrainingSession> = listOf(
            session(daysAgo = 1, avgWeightKg = kg),
            session(daysAgo = 3, avgWeightKg = kg),
            session(daysAgo = 5, avgWeightKg = kg),
            session(daysAgo = 8,  avgWeightKg = 60.0), session(daysAgo = 10, avgWeightKg = 60.0),
            session(daysAgo = 12, avgWeightKg = 60.0), session(daysAgo = 15, avgWeightKg = 60.0),
            session(daysAgo = 17, avgWeightKg = 60.0), session(daysAgo = 19, avgWeightKg = 60.0),
            session(daysAgo = 22, avgWeightKg = 60.0), session(daysAgo = 24, avgWeightKg = 60.0),
            session(daysAgo = 26, avgWeightKg = 60.0)
        )
        val lightResult = FatigueEngine.calcFatigueToFitnessRatio(historyAtWeight(60.0))
        val heavyResult = FatigueEngine.calcFatigueToFitnessRatio(historyAtWeight(90.0))
        assertNotNull(lightResult.ratio)
        assertNotNull(heavyResult.ratio)
        assertTrue("Heavy ratio ${heavyResult.ratio} should exceed light ratio ${lightResult.ratio}",
            heavyResult.ratio!! > lightResult.ratio!!)
    }

    @Test
    fun extremelyHeavyWeight_weightFactorClampedAt4() {
        // 400kg and 240kg should produce the same load per session
        // because weightFactor is clamped at (240/60 = 4.0)
        fun historyWithAcuteWeight(kg: Double): Double {
            val history = listOf(
                session(daysAgo = 1, avgWeightKg = kg),
                session(daysAgo = 3, avgWeightKg = 60.0),
                session(daysAgo = 5, avgWeightKg = 60.0),
                session(daysAgo = 8,  avgWeightKg = 60.0), session(daysAgo = 10, avgWeightKg = 60.0),
                session(daysAgo = 13, avgWeightKg = 60.0), session(daysAgo = 16, avgWeightKg = 60.0),
                session(daysAgo = 19, avgWeightKg = 60.0), session(daysAgo = 22, avgWeightKg = 60.0)
            )
            return FatigueEngine.calcFatigueToFitnessRatio(history).ratio ?: 0.0
        }
        val ratio240kg = historyWithAcuteWeight(240.0)
        val ratio400kg = historyWithAcuteWeight(400.0)
        assertEquals("240kg and 400kg should produce the same ratio (both clamped at 4x)",
            ratio240kg, ratio400kg, 0.001)
    }

    @Test
    fun poorSessionFeel_increasesPerceivedLoad() {
        // Feel=1 (terrible) multiplies load by 1.4
        // Feel=3 (normal) multiplies by 1.0
        // A session with feel=1 should produce higher ratio than feel=3
        fun historyWithFeel(feel: Int): Double {
            val history = listOf(
                session(daysAgo = 1, sessionFeel = feel),
                session(daysAgo = 3, sessionFeel = feel),
                session(daysAgo = 5, sessionFeel = feel),
                session(daysAgo = 8,  sessionFeel = 3), session(daysAgo = 10, sessionFeel = 3),
                session(daysAgo = 13, sessionFeel = 3), session(daysAgo = 16, sessionFeel = 3),
                session(daysAgo = 19, sessionFeel = 3), session(daysAgo = 22, sessionFeel = 3)
            )
            return FatigueEngine.calcFatigueToFitnessRatio(history).ratio ?: 0.0
        }
        val normalFeel = historyWithFeel(3)
        val poorFeel = historyWithFeel(1)
        assertTrue("Poor feel ratio $poorFeel should be higher than normal feel ratio $normalFeel",
            poorFeel > normalFeel)
    }

    @Test
    fun greatSessionFeel_decreasesPerceivedLoad() {
        // Feel=5 (great) multiplies load by 0.6 — user found it easy
        fun historyWithFeel(feel: Int): Double {
            val history = listOf(
                session(daysAgo = 1, sessionFeel = feel),
                session(daysAgo = 3, sessionFeel = feel),
                session(daysAgo = 5, sessionFeel = feel),
                session(daysAgo = 8,  sessionFeel = 3), session(daysAgo = 10, sessionFeel = 3),
                session(daysAgo = 13, sessionFeel = 3), session(daysAgo = 16, sessionFeel = 3),
                session(daysAgo = 19, sessionFeel = 3), session(daysAgo = 22, sessionFeel = 3)
            )
            return FatigueEngine.calcFatigueToFitnessRatio(history).ratio ?: 0.0
        }
        val normalFeel = historyWithFeel(3)
        val greatFeel = historyWithFeel(5)
        assertTrue("Great feel ratio $greatFeel should be lower than normal feel ratio $normalFeel",
            greatFeel < normalFeel)
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
