package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var testDb: AppDatabase? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val application = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testDb = db
        AppDatabase.setTestDatabase(db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDb?.close()
        testDb = null
        AppDatabase.setTestDatabase(null)
    }

    @Test
    fun addCustomExercise_insertsExerciseRowWhenNameIsNew() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dao = testDb!!.fitnessDao()
        
        val viewModel = TrainViewModel(application)
        
        // Wait for VM init seeding if any (or let it finish)
        testScheduler.advanceUntilIdle()

        val planExercise = PlanExercise(
            id = 9999L,
            planSessionId = 1L,
            name = "Custom Super Press",
            muscleGroup = "Chest",
            sets = 3,
            repsMin = 8,
            repsMax = 12,
            weight = 0.0,
            restSeconds = 90,
            notes = "Test custom exercise insertion"
        )
        
        viewModel.addCustomExercise(planExercise)
        
        // Poll database with real Thread.sleep to let background Dispatchers.IO finish writing
        var inserted: Exercise? = null
        for (i in 1..30) {
            testScheduler.advanceUntilIdle()
            inserted = dao.getExerciseById("custom-super-press")
            if (inserted != null) break
            Thread.sleep(100)
        }
        
        assertNotNull("Exercise should be inserted into the database", inserted)
        assertEquals("Custom Super Press", inserted?.name)
        assertEquals("User Created", inserted?.category)
        assertEquals("Chest", inserted?.primaryMuscle)
        
        val metadata = dao.getMetadataForExercise("custom-super-press")
        assertNotNull("ExerciseMetadata should also be inserted", metadata)
    }

    @Test
    fun activatePlan_updatesActiveStatus() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dao = testDb!!.fitnessDao()

        // Clear existing plans
        dao.deactivateAllPlans()

        val plan1 = WorkoutPlan(
            id = 1010L,
            name = "Test Plan 1",
            goal = "Strength",
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        val plan2 = WorkoutPlan(
            id = 2020L,
            name = "Test Plan 2",
            goal = "Hypertrophy",
            isActive = false,
            createdAt = System.currentTimeMillis()
        )

        dao.insertWorkoutPlan(plan1)
        dao.insertWorkoutPlan(plan2)

        val viewModel = TrainViewModel(application)
        testScheduler.advanceUntilIdle()

        viewModel.activatePlan(2020L)
        
        // Poll database with real Thread.sleep to let background Dispatchers.IO finish writing
        var p1: WorkoutPlan? = null
        var p2: WorkoutPlan? = null
        for (i in 1..30) {
            testScheduler.advanceUntilIdle()
            val plans = dao.getAllPlans()
            p1 = plans.find { it.id == 1010L }
            p2 = plans.find { it.id == 2020L }
            if (p2 != null && p2.isActive && p1 != null && !p1.isActive) {
                break
            }
            Thread.sleep(100)
        }

        assertNotNull("Plan 2 should exist", p2)
        assertTrue("Plan 2 should be active", p2?.isActive ?: false)
        assertFalse("Plan 1 should be deactivated", p1?.isActive ?: true)
    }
}
