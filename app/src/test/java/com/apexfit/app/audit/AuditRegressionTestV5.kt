package com.apexfit.app.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.FitnessDao
import com.apexfit.app.data.NutritionEntry
import com.apexfit.app.data.NutritionTargets
import com.apexfit.app.data.PersonalRecord
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.data.TrainingSession
import com.apexfit.app.data.ActiveSet
import com.apexfit.app.data.WeightEntry
import com.apexfit.app.utils.AlgorithmEngine
import com.apexfit.app.utils.ComplianceEngine
import com.apexfit.app.utils.DateTimeUtils
import com.apexfit.app.utils.FatigueDoseCalculator
import com.apexfit.app.utils.FatigueEngine
import com.apexfit.app.utils.GoalEngine
import com.apexfit.app.ProgressDataAggregation
import com.apexfit.app.utils.SystemicFatigueCalculator
import com.apexfit.app.utils.TdeeEngine
import com.apexfit.app.utils.getDateDaysAgo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuditRegressionTestV5 {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: FitnessDao

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.fitnessDao()
        dao.insertExercise(
            Exercise(
                id = "bench",
                name = "Bench Press",
                category = "Barbell",
                primaryMuscle = "Chest",
                equipmentRequired = "Barbell"
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun repeatPersonalRecordUpsertsInsteadOfAborting() = runBlocking {
        dao.insertPersonalRecord(
            PersonalRecord("bench_max_weight", "bench", "max_weight", 80.0, "2026-11-01")
        )
        dao.insertPersonalRecord(
            PersonalRecord("bench_max_weight", "bench", "max_weight", 82.5, "2026-11-08")
        )

        val records = dao.getPRsForExercise("bench")
        assertEquals(1, records.count { it.type == "max_weight" })
        assertEquals(82.5, records.single { it.type == "max_weight" }.value, 0.001)
        assertEquals("2026-11-08", records.single { it.type == "max_weight" }.date)
    }

    @Test
    fun latestSetQueriesUseWorkoutChronologyNotSetInsertionOrder() = runBlocking {
        dao.insertTrainingSession(
            TrainingSession(
                id = "newer",
                date = "2026-11-10",
                sessionType = "Upper",
                completed = true,
                durationMinutes = 45,
                sessionFeel = 4,
                startedAt = 2_000L,
                completedAt = 3_000L
            )
        )
        dao.insertTrainingSession(
            TrainingSession(
                id = "older-imported-later",
                date = "2026-11-01",
                sessionType = "Upper",
                completed = true,
                durationMinutes = 45,
                sessionFeel = 4,
                startedAt = 1_000L,
                completedAt = 1_500L
            )
        )

        dao.insertExerciseSet(
            ExerciseSet(
                id = 10L,
                sessionId = "newer",
                exerciseId = "bench",
                exerciseName = "Bench Press",
                muscleGroup = "Chest",
                weight = 80.0,
                reps = 10,
                rpe = 8,
                isWarmup = false,
                completed = true
            )
        )
        dao.insertExerciseSet(
            ExerciseSet(
                id = 999L,
                sessionId = "older-imported-later",
                exerciseId = "bench",
                exerciseName = "Bench Press",
                muscleGroup = "Chest",
                weight = 60.0,
                reps = 10,
                rpe = 8,
                isWarmup = false,
                completed = true
            )
        )

        val lastSessionSets = dao.getLastSetsWithDateForExercises(listOf("bench"))
        assertEquals(1, lastSessionSets.size)
        assertEquals("newer", lastSessionSets.single().sessionId)
        assertEquals(80.0, dao.getLastWeightForExercise("bench") ?: -1.0, 0.001)
        assertEquals("newer", dao.getLastSetForExercise("bench")?.sessionId)
    }

    @Test
    fun bodyweightSetsContributeToReadinessFatigue() {
        val set = ExerciseSet(weight = 0.0, reps = 10, rpe = 8, isWarmup = false, completed = true)

        val muscleDose = FatigueDoseCalculator.doseForSet(
            set = set,
            exerciseName = "Pull-Up",
            bodyWeightKg = 70.0
        )
        val systemicDose = SystemicFatigueCalculator.doseForSet(
            set = set,
            exerciseName = "Pull-Up",
            bodyWeightKg = 70.0
        )

        assertTrue(muscleDose > 0.0)
        assertTrue(systemicDose > 0.0)
    }

    @Test
    fun heatmapCountsOnlyCompletedWorkingSetsAndIncludesBodyweight() {
        val workout = RichTrainingSession(
            date = getDateDaysAgo(0),
            sessionType = "Upper",
            completed = true,
            exercises = listOf(
                ExerciseLog(
                    id = "push-up",
                    name = "Push-Up",
                    muscleGroup = "Chest",
                    sets = listOf(
                        ExerciseSet(0.0, 12, 8, false, true),
                        ExerciseSet(100.0, 12, 8, false, false),
                        ExerciseSet(0.0, 10, 6, true, true)
                    )
                )
            )
        )

        val heatmap = FatigueEngine.calcMuscleHeatmap(listOf(workout), days = 7)
        assertEquals(1, heatmap["chest"]?.volume)
        assertTrue((heatmap["chest"]?.intensity ?: 0) > 0)
    }

    @Test
    fun weeklyVolumeExcludesIncompleteWorkingSets() {
        val workout = RichTrainingSession(
            date = getDateDaysAgo(0),
            sessionType = "Upper",
            completed = true,
            exercises = listOf(
                ExerciseLog(
                    id = "bench",
                    name = "Bench Press",
                    muscleGroup = "Chest",
                    sets = listOf(
                        ExerciseSet(10.0, 10, 8, false, true),
                        ExerciseSet(100.0, 10, 8, false, false)
                    )
                )
            )
        )

        val weekly = AlgorithmEngine.calcWeeklyVolumePerMuscle(listOf(workout), weeks = 4)
        assertEquals(100.0, weekly["Chest"]?.single()?.second ?: -1.0, 0.001)
    }

    @Test
    fun complianceCountsMissingDaysAfterTrackingBegins() {
        val target = NutritionTargets(2000, 120, 200, 60, weeklyTrainingSessions = 4)
        val nutrition = listOf(
            NutritionEntry(date = getDateDaysAgo(7), calories = 2000, protein = 120, carbs = 200, fat = 60),
            NutritionEntry(date = getDateDaysAgo(0), calories = 2000, protein = 120, carbs = 200, fat = 60)
        )

        val result = ComplianceEngine.calcComplianceScores(
            nutritionLog = nutrition,
            trainingLog = emptyList(),
            targets = target,
            days = 14
        )

        assertEquals(25, result.calories)
        assertEquals(25, result.protein)
    }

    @Test
    fun adaptiveTdeeFallsBackWhenNutritionCoverageIsSparse() {
        val weights = (0 until 15).map {
            WeightEntry(date = getDateDaysAgo(14 - it), weight = 80.0)
        }
        val nutrition = (0 until 7).map {
            NutritionEntry(
                date = getDateDaysAgo(6 - it),
                calories = 2600,
                protein = 150,
                carbs = 280,
                fat = 80
            )
        }

        val result = TdeeEngine.calcAdaptiveTDEE(
            weightLog = weights,
            nutritionLog = nutrition,
            windowDays = 14,
            heightCm = 175.0,
            ageYears = 30,
            biologicalSex = "male",
            weeklyWorkouts = 4,
            fallbackWeightKg = 80.0
        )

        assertTrue(result.confidence.contains("sparse nutrition coverage"))
    }

    @Test
    fun workoutDurationDoesNotAddRestAfterFinalSet() {
        val exercise = PlanExercise(
            planSessionId = 1,
            exerciseId = "bench",
            name = "Bench Press",
            muscleGroup = "Chest",
            sets = 4,
            repsMin = 8,
            repsMax = 12,
            weight = 50.0,
            restSeconds = 180,
            notes = ""
        )

        assertEquals(11, AlgorithmEngine.estimateSetDurationMinutes(exercise))
    }

    @Test
    fun activeSetsStartUnrated() {
        assertEquals(0, ActiveSet(setNumber = 1, weight = 10.0, reps = 10).rpe)
    }

    @Test
    fun maintenanceGoalHasNoFakeWeightChangeTimeline() {
        val timeline = GoalEngine.calcGoalTimeline(
            currentWeightKg = 75.0,
            goalWeightKg = 75.0,
            resolvedGoal = "Maintain",
            heightCm = 175.0,
            sex = "male"
        )

        assertEquals(0, timeline.weeksToGoal)
        assertEquals(0.0, timeline.weeklyChangeKg, 0.001)
        assertTrue(timeline.summaryLine.contains("Maintain"))
    }

    @Test
    fun recoveryAggregationUsesCompletionTimestampInsteadOfMidnight() {
        val completion = System.currentTimeMillis() - 15 * 60 * 1000L
        val session = TrainingSession(
            id = "recent",
            date = LocalDate.now().toString(),
            sessionType = "Upper",
            completed = true,
            durationMinutes = 45,
            sessionFeel = 4,
            startedAt = completion - 45 * 60 * 1000L,
            completedAt = completion
        )
        val set = ExerciseSet(
            sessionId = "recent",
            exerciseId = "bench",
            exerciseName = "Bench Press",
            muscleGroup = "Chest",
            weight = 50.0,
            reps = 10,
            rpe = 8,
            isWarmup = false,
            completed = true
        )

        val moments = ProgressDataAggregation.latestSessionMoments(
            listOf(session),
            listOf(set),
            listOf("Chest")
        ) { group, muscle -> group.equals(muscle, ignoreCase = true) }

        assertEquals(completion, moments["Chest"]?.timestampMs)
    }

    @Test
    fun epochDateHelperPreservesLocalStartDate() {
        val localDate = LocalDate.of(2026, 11, 15)
        val epoch = localDate.atTime(LocalTime.of(23, 55))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals("2026-11-15", DateTimeUtils.dateStringForEpochMillis(epoch))
    }
}
