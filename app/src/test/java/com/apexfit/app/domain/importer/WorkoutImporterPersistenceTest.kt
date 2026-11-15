package com.apexfit.app.domain.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.WorkoutPlan
import com.apexfit.app.data.repository.FitnessRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutImporterPersistenceTest {

    @Test
    fun importedPlanSavesAtomicallyUsingExistingSchemaAndExerciseIds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.fitnessDao()
            dao.insertExercises(
                listOf(
                    Exercise(
                        id = "barbell-bench-press",
                        name = "Barbell Bench Press",
                        category = "Barbell",
                        primaryMuscle = "Chest",
                        equipmentRequired = "Barbell"
                    ),
                    Exercise(
                        id = "barbell-row",
                        name = "Barbell Row",
                        category = "Barbell",
                        primaryMuscle = "Back",
                        equipmentRequired = "Barbell"
                    )
                )
            )
            val previousId = dao.insertWorkoutPlan(
                WorkoutPlan(name = "Previous", goal = "General", isActive = true)
            )

            val repository = FitnessRepositoryImpl(
                db,
                dao,
                DataStoreManager.getInstance(context)
            )

            val sessions = listOf(
                PlanSession(
                    id = -1,
                    planId = 0,
                    label = "Push",
                    day = "Monday",
                    focus = "Chest"
                ),
                PlanSession(
                    id = -2,
                    planId = 0,
                    label = "Pull",
                    day = "Wednesday",
                    focus = "Back"
                )
            )
            val exercises = listOf(
                PlanExercise(
                    id = 0,
                    planSessionId = -1,
                    exerciseId = "barbell-bench-press",
                    name = "Barbell Bench Press",
                    muscleGroup = "Chest",
                    sets = 3,
                    repsMin = 8,
                    repsMax = 10,
                    weight = 70.0,
                    weightUnit = "kg",
                    restSeconds = 120,
                    notes = ""
                ),
                PlanExercise(
                    id = 0,
                    planSessionId = -2,
                    exerciseId = "barbell-row",
                    name = "Barbell Row",
                    muscleGroup = "Back",
                    sets = 4,
                    repsMin = 8,
                    repsMax = 8,
                    weight = 60.0,
                    weightUnit = "kg",
                    restSeconds = 120,
                    notes = "Imported group: A"
                )
            )

            repository.updateWorkoutPlan(
                WorkoutPlan(
                    name = "Imported Reddit Plan",
                    goal = "General Fitness",
                    isActive = true,
                    daysPerWeek = 2,
                    isTemplate = 0,
                    source = "text_import",
                    importRawText = "Monday Push\nBench Press 3x8"
                ),
                sessions,
                exercises
            )

            val plans = dao.getAllPlans()
            assertEquals(2, plans.size)
            val previous = plans.single { it.id == previousId }
            assertFalse(previous.isActive)

            val imported = plans.single { it.name == "Imported Reddit Plan" }
            assertTrue(imported.isActive)
            assertEquals("text_import", imported.source)
            assertEquals("Monday Push\nBench Press 3x8", imported.importRawText)
            assertEquals(2, imported.daysPerWeek)

            val savedSessions = dao.getSessionsForPlan(imported.id)
            assertEquals(2, savedSessions.size)
            assertTrue(savedSessions.all { it.id > 0 && it.planId == imported.id })

            val savedExercises = savedSessions.flatMap { dao.getExercisesForSession(it.id) }
            assertEquals(2, savedExercises.size)
            assertEquals(setOf("barbell-bench-press", "barbell-row"), savedExercises.map { it.exerciseId }.toSet())
            assertTrue(savedExercises.none { it.planSessionId < 0 })
            assertTrue("Known imported exercises must not be duplicated as custom exercises", dao.getUserCreatedExercises().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun failedImportedPlanDoesNotLeavePartialRowsOrDeactivateCurrentPlan() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.fitnessDao()
            dao.insertExercises(
                listOf(
                    Exercise(
                        id = "barbell-bench-press",
                        name = "Barbell Bench Press",
                        category = "Barbell",
                        primaryMuscle = "Chest",
                        equipmentRequired = "Barbell"
                    )
                )
            )
            val previousId = dao.insertWorkoutPlan(
                WorkoutPlan(name = "Current", goal = "General", isActive = true)
            )
            val repository = FitnessRepositoryImpl(
                db,
                dao,
                DataStoreManager.getInstance(context)
            )

            val orphan = PlanExercise(
                planSessionId = -999,
                exerciseId = "barbell-bench-press",
                name = "Barbell Bench Press",
                muscleGroup = "Chest",
                sets = 3,
                repsMin = 8,
                repsMax = 8,
                weight = 0.0,
                weightUnit = "kg",
                restSeconds = 120,
                notes = ""
            )

            try {
                repository.updateWorkoutPlan(
                    WorkoutPlan(
                        name = "Broken Import",
                        goal = "General Fitness",
                        isActive = true,
                        daysPerWeek = 1,
                        source = "text_import",
                        importRawText = "broken"
                    ),
                    sessions = emptyList(),
                    exercises = listOf(orphan)
                )
                fail("Expected invalid imported plan to fail")
            } catch (_: IllegalArgumentException) {
                // expected
            }

            assertTrue(dao.getAllPlans().none { it.name == "Broken Import" })
            val current = requireNotNull(dao.getAllPlans().singleOrNull { it.id == previousId })
            assertTrue("The previously active plan must remain active after rollback", current.isActive)
            assertTrue(dao.getSessionsForPlan(previousId).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun inactiveImportDoesNotDeactivateExistingActivePlan() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.fitnessDao()
            val currentId = dao.insertWorkoutPlan(
                WorkoutPlan(name = "Current", goal = "General", isActive = true)
            )
            val repository = FitnessRepositoryImpl(
                db,
                dao,
                DataStoreManager.getInstance(context)
            )

            repository.updateWorkoutPlan(
                WorkoutPlan(
                    name = "Imported Inactive",
                    goal = "General Fitness",
                    isActive = false,
                    daysPerWeek = 0,
                    source = "text_import",
                    importRawText = "draft"
                ),
                sessions = emptyList(),
                exercises = emptyList()
            )

            assertTrue(requireNotNull(dao.getAllPlans().single { it.id == currentId }).isActive)
            assertFalse(dao.getAllPlans().single { it.name == "Imported Inactive" }.isActive)
        } finally {
            db.close()
        }
    }
}
