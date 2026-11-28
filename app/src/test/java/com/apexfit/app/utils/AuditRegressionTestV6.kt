package com.apexfit.app.utils

import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.data.NutritionEntry
import com.apexfit.app.data.WeightEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditRegressionTestV6 {

    private fun session(daysAgo: Int, rpe: Int = 8): RichTrainingSession =
        RichTrainingSession(
            date = getDateDaysAgo(daysAgo),
            sessionType = "Test",
            completed = true,
            exercises = listOf(
                ExerciseLog(
                    id = "bench",
                    name = "Bench Press",
                    muscleGroup = "chest",
                    sets = listOf(
                        ExerciseSet(
                            weight = 50.0,
                            reps = 10,
                            rpe = rpe,
                            isWarmup = false,
                            completed = true
                        )
                    )
                )
            )
        )

    @Test
    fun sevenDayWindowContainsExactlyTodayAndPreviousSixDates() {
        assertEquals(LocalDate.now().minusDays(6).toString(), getWindowStartDate(7))
        assertEquals(LocalDate.now().toString(), getWindowStartDate(1))
    }

    @Test
    fun effectiveSetsDoNotLeakAnEighthCalendarDateIntoSevenDayWindow() {
        val result = AlgorithmEngine.calcEffectiveSets(
            trainingLog = listOf(session(daysAgo = 6), session(daysAgo = 7)),
            days = 7
        )

        // One RPE-8 set = 0.75 effective sets. The set exactly seven days ago
        // is the eighth calendar date and must be excluded.
        assertEquals(0.75, result["chest"] ?: 0.0, 0.001)
    }

    @Test
    fun heatmapDoesNotLeakAnEighthCalendarDateIntoSevenDayWindow() {
        val result = FatigueEngine.calcMuscleHeatmap(
            trainingLog = listOf(session(daysAgo = 6), session(daysAgo = 7)),
            days = 7
        )

        assertEquals(1, result["chest"]?.volume)
    }

    @Test
    fun plateauRequiresWeightObservationsThatActuallySpanRequestedWindow() {
        val weights = listOf(
            WeightEntry(getDateDaysAgo(5), 80.0),
            WeightEntry(getDateDaysAgo(0), 80.0)
        )
        val nutrition = (0 until 14).map {
            NutritionEntry(
                date = getDateDaysAgo(it),
                calories = 2000,
                protein = 150,
                carbs = 200,
                fat = 60
            )
        }
        val training = listOf(session(daysAgo = 20), session(daysAgo = 2))

        val result = PlateauEngine.detectPlateau(
            weightLog = weights,
            nutritionLog = nutrition,
            trainingLog = training,
            goal = "lose fat",
            windowDays = 14
        )

        assertFalse(result.isPlateaued)
    }

    @Test
    fun multipleSessionsOnOneDayDoNotInflateTrainingAdherence() {
        // An older nutrition entry establishes a full 7-day evaluation window.
        // With a two-day/week target, two sessions performed on the same day count
        // as one training day: 1 of 2 expected days = 50%, not 100%.
        val nutrition = listOf(
            NutritionEntry(date = getDateDaysAgo(7), calories = 2000, protein = 120, carbs = 200, fat = 60),
            NutritionEntry(date = getCurrentDate(), calories = 2000, protein = 120, carbs = 200, fat = 60)
        )
        val duplicateDaySessions = listOf(session(daysAgo = 1), session(daysAgo = 1))

        val result = ComplianceEngine.calcComplianceScores(
            nutritionLog = nutrition,
            trainingLog = duplicateDaySessions,
            targets = NutritionTargets(2000, 120, 200, 60, weeklyTrainingSessions = 2),
            days = 7
        )

        assertEquals(50, result.training)
    }

    @Test
    fun duplicateSessionsDoNotUnlockWorkloadBaselineWithoutSevenTrainingDays() {
        val history = listOf(
            session(0), session(0), session(0),
            session(8), session(8), session(8),
            session(15)
        )

        val result = FatigueEngine.calcFatigueToFitnessRatio(history)
        assertEquals(null, result.ratio)
        assertEquals("No Data", result.statusLabel)
    }

    @Test
    fun workloadCopyDoesNotPresentRatioThresholdAsOptimalOrInjuryPrediction() {
        val history = listOf(
            session(1), session(3), session(5),
            session(8), session(10), session(12),
            session(15), session(18), session(22), session(26)
        )
        val result = FatigueEngine.calcFatigueToFitnessRatio(history)

        assertTrue(result.statusLabel != "Optimal Load")
        assertFalse(result.recommendation.contains("injury", ignoreCase = true))
        assertFalse(result.recommendation.contains("safe", ignoreCase = true))
    }
}
