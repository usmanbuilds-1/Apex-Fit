package com.apexfit.app.integration

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.WorkoutImportViewModel
import com.apexfit.app.WorkoutImportStage
import com.apexfit.app.data.*
import com.apexfit.app.data.repository.FitnessRepositoryImpl
import com.apexfit.app.domain.importer.FragmentedWorkoutPasteTest
import com.apexfit.app.utils.BodyWeightHistory
import com.apexfit.app.utils.MacroEngine
import com.apexfit.app.utils.WorkoutSchedule
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RotatingPlanWeightIntegrationTest {
    @Test
    fun exactClipboardImportAndWeightEditsShareOneDatabaseAndNutritionPipeline() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.fitnessDao()
        val store = DataStoreManager.getInstance(context)
        val repo = FitnessRepositoryImpl(db, dao, store)
        val importer = WorkoutImportViewModel(repo, dao)
        try {
            store.clearAllData()
            store.saveSettingsProfile("Integration", "Gain Muscle", "kg", "Gym", 175.0, 30, "male", 70.0, 75.0, true, 2400)
            store.saveWeeklyWorkouts(4) // Must not override the actual 3-day rotation.
            val legacyId = dao.insertWorkoutPlan(WorkoutPlan(name = "Previous", goal = "General", isActive = true))
            val names = listOf("Leg Press", "Hack Squat", "Incline dumbbell press",
                "Chest-supported incline dumbbell row", "Incline dumbbell curl", "Dead bug",
                "B-stance dumbbell Romanian deadlift", "Single-arm dumbbell row towards hip",
                "Seated dumbbell lateral raise", "Chest-supported rear delt fly",
                "Seated overhead dumbbell triceps extension")
            dao.insertExercises(names.mapIndexed { index, name ->
                Exercise(id = "integration-$index", name = name, category = "Strength",
                    primaryMuscle = "Other", equipmentRequired = "Dumbbell")
            })

            val date = LocalDate.now()
            val dateString = date.toString()
            repo.logWeightEntry(WeightEntry(date = dateString, time = "07:00", weight = 61.0))
            repo.logWeightEntry(WeightEntry(date = dateString, time = "07:00", weight = 62.0))
            assertEquals(62.0, repo.getCurrentWeightFlow().first(), 1e-9)

            // Use the actual import ViewModel, including its production save path.
            importer.updateInput(FragmentedWorkoutPasteTest().source())
            importer.parse()
            val parsed = withTimeout(20_000) { importer.uiState.first { !it.isParsing && (it.stage == WorkoutImportStage.REVIEW || it.errorMessage != null) } }
            val draft = requireNotNull(parsed.draft) { parsed.errorMessage.orEmpty() }
            assertEquals(2, draft.cycleWeeks)
            assertEquals(10, draft.days.sumOf { it.exercises.size })
            for (day in draft.days) {
                for (exercise in day.exercises) {
                    if (exercise.sourceName.contains(" / ")) {
                        assertNull(exercise.selectedExerciseId)
                        importer.selectExercise(day.key, exercise.key, "integration-0")
                    } else {
                        assertNotNull(exercise.selectedExerciseId)
                        importer.confirmSuggestedMatch(day.key, exercise.key)
                    }
                }
            }
            importer.updatePlanName("A/B integration")
            assertTrue(importer.uiState.value.draft!!.validationErrors().toString(), importer.uiState.value.draft!!.validationErrors().isEmpty())
            val saved = CompletableDeferred<Unit>()
            importer.save { saved.complete(Unit) }
            withTimeout(20_000) { saved.await() }
            val plan = requireNotNull(dao.getActivePlan())
            assertEquals(2, plan.cycleWeeks)
            val slots = dao.getSessionsForPlan(plan.id)
            assertEquals(6, slots.size)
            assertEquals(30, slots.sumOf { dao.getExercisesForSession(it.id).size })
            assertEquals(2, dao.getWeightEntries().size)
            assertEquals(62.0, repo.getCurrentWeightFlow().first(), 1e-9)
            val anchor = LocalDate.parse(requireNotNull(plan.cycleAnchorMonday))
            assertEquals("Workout A", WorkoutSchedule.sessionForDate(plan, slots, anchor)?.label)
            assertEquals("Workout B", WorkoutSchedule.sessionForDate(plan, slots, anchor.plusWeeks(1))?.label)
            assertEquals("Workout A", WorkoutSchedule.sessionForDate(plan, slots, anchor.plusWeeks(2))?.label)
            assertEquals(3, repo.getTrainingFrequencyFlow().first())
            assertEquals(3, repo.getAverageTrainingFrequencyFlow().first())

            val todaySlot = WorkoutSchedule.sessionForDate(plan, slots, date)
            val expectedExercises = todaySlot?.let { dao.getExercisesForSession(it.id).map { ex -> ex.id }.toSet() }.orEmpty()
            withTimeout(20_000) { repo.todayExercisesFlow.first { list -> list.map { it.id }.toSet() == expectedExercises } }

            suspend fun assertTargets(weight: Double, frequency: Int, training: Boolean) {
                val expected = MacroEngine.calcCycledTargets(2400, frequency, "Gain Muscle", weight, 175.0, "male")
                val calories = if (training) expected.trainingDayCalories else expected.restDayCalories
                val protein = if (training) expected.trainingDayProtein else expected.restDayProtein
                val carbs = if (training) expected.trainingDayCarbs else expected.restDayCarbs
                val actual = withTimeout(20_000) { repo.sharedTargetsFlow.first {
                    it.calories == calories && it.protein == protein && it.carbs == carbs &&
                        it.fat == expected.fat && it.weeklyTrainingSessions == frequency
                } }
                assertEquals(expected.fat, actual.fat)
            }

            assertTargets(62.0, 3, todaySlot != null)
            val latest = dao.getWeightEntries().first()
            assertTrue(repo.editWeightEntry(latest.id, 80.0))
            assertEquals(2, dao.getWeightEntries().size)
            assertEquals(80.0, repo.getCurrentWeightFlow().first(), 1e-9)
            assertEquals(70.5, BodyWeightHistory.daily(dao.getWeightEntries()).single().weight, 1e-9)
            assertTargets(80.0, 3, todaySlot != null)
            repo.removeWeightEntry(latest.id)
            assertEquals(61.0, repo.getCurrentWeightFlow().first(), 1e-9)
            assertTargets(61.0, 3, todaySlot != null)
            assertEquals(slots, dao.getSessionsForPlan(plan.id))

            db.withTransaction {
                dao.deactivateAllPlans()
                dao.activatePlan(legacyId)
            }
            assertEquals(4, repo.getTrainingFrequencyFlow().first())
            assertEquals(61.0, repo.getCurrentWeightFlow().first(), 1e-9)
            db.withTransaction {
                dao.deactivateAllPlans()
                dao.initializeCycleAnchor(plan.id, anchor.plusWeeks(8).toString())
                dao.activatePlan(plan.id)
            }
            assertEquals(anchor.toString(), dao.getActivePlan()?.cycleAnchorMonday)
            assertEquals(3, repo.getTrainingFrequencyFlow().first())
            assertEquals(61.0, repo.getCurrentWeightFlow().first(), 1e-9)
            assertTargets(61.0, 3, todaySlot != null)
            assertEquals(1, dao.getWeightEntries().size)
        } finally {
            importer.viewModelScope.cancel()
            repo.appScope.cancel()
            store.clearAllData()
            db.close()
            Dispatchers.resetMain()
        }
    }
}
