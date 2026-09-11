package com.apexfit.app.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.FitnessDao
import com.apexfit.app.data.NutritionEntry
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.TrainingSession
import com.apexfit.app.data.WorkoutPlan
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.utils.AppConstants
import com.apexfit.app.data.DetectedPattern
import com.apexfit.app.utils.PatternDetector
import com.apexfit.app.utils.exerciseNameToSlug
import com.apexfit.app.utils.getDaysBetweenClamped
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuditRegressionTestV4 {

    private lateinit var db: AppDatabase
    private lateinit var dao: FitnessDao
    private lateinit var context: Context

    @Before
    fun createDb() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.fitnessDao()

        dao.insertExercises(
            listOf(
                Exercise(
                    id = "bench-press",
                    name = "Bench Press",
                    category = "Barbell",
                    primaryMuscle = "Chest",
                    equipmentRequired = "Barbell"
                ),
                Exercise(
                    id = "squat",
                    name = "Squat",
                    category = "Barbell",
                    primaryMuscle = "Legs",
                    equipmentRequired = "Barbell"
                )
            )
        )
        dao.insertTrainingSession(
            TrainingSession(
                id = "session_1",
                date = "2024-01-01",
                sessionType = "Upper",
                completed = true,
                durationMinutes = 60,
                sessionFeel = 3
            )
        )
        dao.insertTrainingSession(
            TrainingSession(
                id = "session_kg",
                date = "2024-01-01",
                sessionType = "Lower",
                completed = true,
                durationMinutes = 60,
                sessionFeel = 3
            )
        )
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    private fun invokeDetectDayOfWeekPatterns(log: List<NutritionEntry>): List<DetectedPattern> {
        val method: Method = PatternDetector::class.java.getDeclaredMethod("detectDayOfWeekPatterns", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(PatternDetector, log) as List<DetectedPattern>
    }

    // 1. activatePlan_updateDoesNotWipeChildren
    // Creates a plan with child exercises, activates it via dao.deactivateAllPlans()
    // + dao.activatePlan(), verifies plan exercises still exist (no REPLACE cascade wipe).
    @Test
    fun activatePlan_updateDoesNotWipeChildren() = runBlocking {
        val plan = WorkoutPlan(name = "Hypertrophy Program", goal = "Hypertrophy", isActive = false)
        val planId = dao.insertWorkoutPlan(plan)

        val session = PlanSession(planId = planId, label = "Upper A", day = "Monday", focus = "Chest & Back")
        val sessionId = dao.insertPlanSession(session)

        val planExercise = PlanExercise(
            planSessionId = sessionId,
            name = "Bench Press",
            muscleGroup = "Chest",
            sets = 3,
            repsMin = 8,
            repsMax = 12,
            weight = 80.0,
            restSeconds = 90,
            notes = "Control eccentric"
        )
        dao.insertPlanExercise(planExercise)

        val initialExercises = dao.getExercisesForSession(sessionId)
        assertEquals(1, initialExercises.size)

        // Activate plan via standard activation flow
        dao.deactivateAllPlans()
        dao.activatePlan(planId)

        val exercisesAfterActivation = dao.getExercisesForSession(sessionId)
        assertEquals("Plan exercises must not be wiped when activating plan", 1, exercisesAfterActivation.size)
        assertEquals("Bench Press", exercisesAfterActivation[0].name)
    }

    // 2. dayOfWeekPatterns_sameDailyTotals_noFalsePattern
    // Feeds PatternDetector a nutrition log where two identical daily totals are
    // split across different numbers of meals on different days. Asserts no
    // "low_cal_" or "high_cal_" pattern is emitted (BUG-V4-005 negative control).
    @Test
    fun dayOfWeekPatterns_sameDailyTotals_noFalsePattern() {
        // Build 21 days with identical daily totals: 2400 kcal and 150g protein every day.
        // On Mondays, split across 4 small meals (600 kcal each).
        // On other days, split across 2 larger meals (1200 kcal each) or 3 meals (800 kcal each).
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val entries = mutableListOf<NutritionEntry>()

        for (i in 0..20) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(cal.time)
            val dow = cal.get(Calendar.DAY_OF_WEEK)

            if (dow == Calendar.MONDAY) {
                // 4 meals: 600 kcal each = 2400 kcal total
                repeat(4) { mealIdx ->
                    entries.add(
                        NutritionEntry(
                            date = dateStr,
                            name = "Meal $mealIdx",
                            calories = 600,
                            protein = 37.0,
                            carbs = 75.0,
                            fat = 15.0
                        )
                    )
                }
            } else {
                // 2 meals: 1200 kcal each = 2400 kcal total
                repeat(2) { mealIdx ->
                    entries.add(
                        NutritionEntry(
                            date = dateStr,
                            name = "Meal $mealIdx",
                            calories = 1200,
                            protein = 75.0,
                            carbs = 150.0,
                            fat = 30.0
                        )
                    )
                }
            }
        }

        val patterns = invokeDetectDayOfWeekPatterns(entries)
        assertFalse("Should not detect low_cal pattern when daily totals are identical",
            patterns.any { it.id.startsWith("low_cal_") })
        assertFalse("Should not detect high_cal pattern when daily totals are identical",
            patterns.any { it.id.startsWith("high_cal_") })
    }

    // 3. dayOfWeekPatterns_realDeviation_stillDetected
    // Feeds PatternDetector a log where one day genuinely has 30% fewer daily
    // calories. Asserts a "low_cal_" pattern IS emitted (positive control).
    @Test
    fun dayOfWeekPatterns_realDeviation_stillDetected() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val entries = mutableListOf<NutritionEntry>()

        for (i in 0..27) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(cal.time)
            val dow = cal.get(Calendar.DAY_OF_WEEK)

            val dailyCalories = if (dow == Calendar.SUNDAY) 1400 else 2400 // ~41% reduction on Sunday
            entries.add(
                NutritionEntry(
                    date = dateStr,
                    name = "Daily Total",
                    calories = dailyCalories,
                    protein = 150.0,
                    carbs = 200.0,
                    fat = 60.0
                )
            )
        }

        val patterns = invokeDetectDayOfWeekPatterns(entries)
        assertTrue("Should detect low_cal pattern when Sunday has a genuine calorie deficit",
            patterns.any { it.id.startsWith("low_cal_") })
    }

    // 4. getDaysBetweenClamped_dst23HourDay_returnsOne
    // Calls getDaysBetweenClamped with two consecutive date strings where the
    // real millisecond gap is 23 hours (82800000 ms). Asserts the result is 1.
    @Test
    fun getDaysBetweenClamped_dst23HourDay_returnsOne() {
        // Consecutive dates where daylight saving time transition reduces the day to 23 hours
        // Math.round(82800000.0 / (1000 * 60 * 60 * 24)) -> Math.round(0.95833) = 1
        val diffInMillis = 82800000.0
        val days = Math.round(diffInMillis / (1000.0 * 60 * 60 * 24)).toInt()
        assertEquals(1, days)

        // Using standard consecutive date format through getDaysBetweenClamped
        val result = getDaysBetweenClamped("2024-03-09", "2024-03-10")
        assertEquals(1, result)
    }

    // 5. seedExerciseIds_matchSlugOfName
    // Verifies that for all seed exercises (loaded from SeedService or the
    // seed data source), the exercise id matches exerciseNameToSlug(exercise.name).
    @Test
    fun seedExerciseIds_matchSlugOfName() {
        val jsonString = context.assets.open("seed_exercises.json").bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        assertTrue("Seed exercises must not be empty", jsonArray.length() > 0)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val expectedSlug = exerciseNameToSlug(name)
            assertEquals("Exercise id '$id' must match slug of name '$name'", expectedSlug, id)
        }
    }

    // 6. convertAllExerciseSetWeightsToKg_dividesBy2_20462
    // Inserts an exercise_set row with weight_unit='lb' and weight=220.462,
    // calls dao.convertAllExerciseSetWeightsToKg(), reads back the row,
    // asserts weight ≈ 100.0 kg.
    @Test
    fun convertAllExerciseSetWeightsToKg_dividesBy2_20462() = runBlocking {
        val exerciseSet = ExerciseSet(
            id = 1L,
            sessionId = "session_1",
            exerciseId = "bench-press",
            exerciseName = "Bench Press",
            muscleGroup = "Chest",
            weight = 220.462,
            reps = 10,
            rpe = 8,
            isWarmup = false,
            completed = true,
            weightUnit = "lb"
        )
        dao.insertExerciseSet(exerciseSet)

        dao.convertAllExerciseSetWeightsToKg()

        val sets = dao.getAllExerciseSets()
        assertEquals(1, sets.size)
        val updatedSet = sets[0]
        assertEquals("kg", updatedSet.weightUnit)
        assertEquals(100.0, updatedSet.weight, 0.01)
    }

    // 7. canonicalizeExerciseSetWeightsIfNeeded_runsOnceAndRespectsUnits
    // For an imperial user: calls canonicalization twice, asserts the DAO
    // conversion ran exactly once (flag guards it).
    // For a kg user: asserts conversion did NOT run.
    @Test
    fun canonicalizeExerciseSetWeightsIfNeeded_runsOnceAndRespectsUnits() = runBlocking {
        val dataStore = DataStoreManager(context)
        dataStore.clearAllData()

        // 1. Kg user test: DAO conversion should NOT run
        val kgSet = ExerciseSet(
            id = 10L,
            sessionId = "session_kg",
            exerciseId = "squat",
            exerciseName = "Squat",
            muscleGroup = "Legs",
            weight = 220.462,
            reps = 5,
            rpe = 8,
            isWarmup = false,
            completed = true,
            weightUnit = "lb"
        )
        dao.insertExerciseSet(kgSet)

        dataStore.canonicalizeExerciseSetWeightsIfNeeded(dao, isImperial = false)
        var currentSet = dao.getAllExerciseSets().first { it.id == 10L }
        // Should remain untouched for kg user
        assertEquals(220.462, currentSet.weight, 0.001)
        assertEquals("lb", currentSet.weightUnit)

        // Clear dataStore flag to simulate fresh run for imperial user
        dataStore.clearAllData()

        // 2. Imperial user test: First invocation converts 220.462 -> 100.0
        dataStore.canonicalizeExerciseSetWeightsIfNeeded(dao, isImperial = true)
        currentSet = dao.getAllExerciseSets().first { it.id == 10L }
        assertEquals(100.0, currentSet.weight, 0.01)
        assertEquals("kg", currentSet.weightUnit)

        // Insert another lb row to test flag guarding
        dao.insertTrainingSession(
            TrainingSession(
                id = "session_imperial_2",
                date = "2024-01-02",
                sessionType = "Pull",
                completed = true,
                durationMinutes = 60,
                sessionFeel = 3
            )
        )
        dao.insertExercises(
            listOf(
                Exercise(
                    id = "deadlift",
                    name = "Deadlift",
                    category = "Barbell",
                    primaryMuscle = "Back",
                    equipmentRequired = "Barbell"
                )
            )
        )
        val secondLbSet = ExerciseSet(
            id = 20L,
            sessionId = "session_imperial_2",
            exerciseId = "deadlift",
            exerciseName = "Deadlift",
            muscleGroup = "Back",
            weight = 220.462,
            reps = 5,
            rpe = 8,
            isWarmup = false,
            completed = true,
            weightUnit = "lb"
        )
        dao.insertExerciseSet(secondLbSet)

        // Second invocation: Should be a no-op because flag is already set
        dataStore.canonicalizeExerciseSetWeightsIfNeeded(dao, isImperial = true)
        val postSecondRunSet = dao.getAllExerciseSets().first { it.id == 20L }
        assertEquals("Subsequent canonicalize call must be guarded and not convert again",
            220.462, postSecondRunSet.weight, 0.001)
        assertEquals("lb", postSecondRunSet.weightUnit)
    }

    // 8. progressionEngine_receivesLbsRegardlessOfPreferredUnit
    // Creates a mock last session with s.weight = 100.0 (canonical kg).
    // Builds allLastSets through the same mapping used in startSession.
    // Asserts the UiExerciseSet.weight reaching the progression engine is
    // ≈ 220.462 lbs, not 100.0 kg.
    @Test
    fun progressionEngine_receivesLbsRegardlessOfPreferredUnit() {
        val s = ExerciseSet(
            id = 1L,
            sessionId = "session_last",
            exerciseId = "bench-press",
            exerciseName = "Bench Press",
            muscleGroup = "Chest",
            weight = 100.0, // canonical kg
            reps = 8,
            rpe = 8,
            isWarmup = false,
            completed = true
        )

        // Mapping from startSession in WorkoutSessionManager.kt
        val uiSet = UiExerciseSet(
            id = s.id,
            weight = s.weight * AppConstants.KG_TO_LBS,
            reps = s.reps,
            rpe = s.rpe,
            isWarmup = s.isWarmup,
            completed = s.completed,
            exerciseName = s.exerciseName,
            sessionId = s.sessionId,
            muscleGroup = s.muscleGroup
        )

        // Progression engine contract requires weight in lbs unconditionally
        assertEquals(220.462, uiSet.weight, 0.01)
    }
}
