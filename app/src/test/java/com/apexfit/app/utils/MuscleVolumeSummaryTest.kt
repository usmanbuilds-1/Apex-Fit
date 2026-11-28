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
    @Test
    fun fractionalVolumeAndDailyExposureRemainSeparateFromObservedCounts() {
        val pull = ExerciseLog("row", "Row", "Lats", listOf(
            ExerciseSet(id = 101, weight = 20.0, reps = 10, rpe = 8, isWarmup = false, completed = true),
            ExerciseSet(id = 102, weight = 20.0, reps = 10, rpe = 8, isWarmup = false, completed = true)
        ), secondaryMuscles = listOf("Biceps", "biceps", "Back"))
        val curl = ExerciseLog("curl", "Curl", "Biceps", listOf(
            ExerciseSet(id = 103, weight = 5.0, reps = 10, rpe = 8, isWarmup = false, completed = true)
        ))
        val sessions = listOf(
            RichTrainingSession("2026-11-15", "Pull", true, exercises = listOf(pull)),
            RichTrainingSession("2026-11-15", "Arms", true, exercises = listOf(curl)),
            RichTrainingSession("2026-11-16", "Pull", true, exercises = listOf(pull.copy(
                sets = listOf(ExerciseSet(id = 104, weight = 20.0, reps = 10, rpe = 8, isWarmup = false, completed = true))
            )))
        )
        val biceps = MuscleVolumeSummary.calculate(sessions, today, MuscleVolumePeriod.MONTH)
            .first { it.muscle == "biceps" }
        assertEquals(1, biceps.directSets)
        assertEquals(3, biceps.secondarySets)
        assertEquals(2.5, biceps.fractionalSets, 0.0)
        assertEquals(1, biceps.directTrainingDays)
        assertEquals(2, biceps.exposureDays)
        assertEquals(listOf(2.0, 0.5), biceps.daily.map { it.fractionalSets })
    }

    @Test
    fun dailyCountsExcludeDuplicatePersistedIdsAndInvalidWorkingSets() {
        val valid = ExerciseSet(id = 91, weight = 0.0, reps = 8, rpe = 8, isWarmup = false, completed = true)
        val exercise = ExerciseLog("pull", "Pull-up", "Back", listOf(
            valid, valid, valid.copy(id = 92, reps = 0), valid.copy(id = 93, weight = Double.NaN),
            valid.copy(id = 94, weight = -1.0), valid.copy(id = 95, isWarmup = true),
            valid.copy(id = 96, completed = false)
        ))
        val session = RichTrainingSession("2026-11-16", "Pull", true, exercises = listOf(exercise))
        val rows = MuscleVolumeSummary.calculate(listOf(session, session), today, MuscleVolumePeriod.MONTH)
        val row = rows.single()
        assertEquals(1, row.directSets)
        assertEquals(1, row.daily.single().directSets)
        assertEquals(1, row.directTrainingDays)
    }

    @Test
    fun explicitWindowIsInclusiveAndDoesNotInventUnloggedDays() {
        val exercise = ExerciseLog("row", "Row", "Back", listOf(ExerciseSet(weight = 20.0, reps = 10)))
        val sessions = listOf("2026-11-01", "2026-11-02", "2026-11-05", "2026-11-06").map {
            RichTrainingSession(it, "Pull", true, exercises = listOf(exercise))
        }
        val row = MuscleVolumeSummary.calculateWindow(sessions, LocalDate.parse("2026-11-02"),
            LocalDate.parse("2026-11-05")).single()
        assertEquals(2, row.directSets)
        assertEquals(listOf("2026-11-02", "2026-11-05"), row.daily.map { it.date.toString() })
        assertEquals(emptyList<MuscleVolumeRow>(), MuscleVolumeSummary.calculateWindow(
            sessions, today, today.minusDays(1)))
    }

    @Test
    fun heatmapCountsEqualDetailAndDoNotTreatIndirectWorkAsDirect() {
        val press = ExerciseLog("press", "Press", "Chest", List(3) {
            ExerciseSet(weight = 20.0, reps = 8)
        }, secondaryMuscles = listOf("Triceps"))
        val pushdown = ExerciseLog("pushdown", "Pushdown", "Triceps", List(2) {
            ExerciseSet(weight = 15.0, reps = 10)
        })
        val rows = MuscleVolumeSummary.calculate(listOf(
            RichTrainingSession(today.toString(), "Push", true, exercises = listOf(press, pushdown))
        ), today, MuscleVolumePeriod.WEEK)
        val map = FatigueEngine.directHeatmap(rows)
        assertEquals(3, map.getValue("chest").volume)
        assertEquals(2, map.getValue("triceps").volume)
        assertEquals(3, rows.first { it.muscle == "triceps" }.secondarySets)
        assertEquals(0, map.getValue("biceps").volume)
        assertEquals(MuscleVolumeSummary.directCounts(rows), map.mapValues { it.value.volume })
    }

    @Test
    fun colorBandIsFixedAndIndependentOfOtherMuscleVolumes() {
        assertEquals(DirectSetBand.NONE, DirectSetBand.of(0))
        assertEquals(DirectSetBand.ONE_TO_FOUR, DirectSetBand.of(1))
        assertEquals(DirectSetBand.ONE_TO_FOUR, DirectSetBand.of(4))
        assertEquals(DirectSetBand.FIVE_TO_NINE, DirectSetBand.of(5))
        assertEquals(DirectSetBand.FIVE_TO_NINE, DirectSetBand.of(9))
        assertEquals(DirectSetBand.TEN_PLUS, DirectSetBand.of(10))
        val chest = MuscleVolumeRow("chest", listOf(MuscleVolumeExercise("Press", 3, 0)))
        val back = MuscleVolumeRow("back", listOf(MuscleVolumeExercise("Row", 100, 0)))
        assertEquals(FatigueEngine.directHeatmap(listOf(chest)).getValue("chest"),
            FatigueEngine.directHeatmap(listOf(chest, back)).getValue("chest"))
    }

    @Test
    fun rollingHeatmapAndCalendarWeekUseExplicitlyDifferentWindows() {
        val exercise = ExerciseLog("row", "Row", "Back", listOf(ExerciseSet(weight = 20.0, reps = 8)))
        val history = listOf("2026-11-15", "2026-11-16", "2026-11-17").map {
            RichTrainingSession(it, "Pull", true, exercises = listOf(exercise))
        }
        assertEquals(2, FatigueEngine.calcMuscleHeatmap(history, today = today).getValue("back").volume)
        val week = MuscleVolumeSummary.calculate(history, today, MuscleVolumePeriod.WEEK)
        assertEquals(1, FatigueEngine.directHeatmap(week).getValue("back").volume)
    }

}
