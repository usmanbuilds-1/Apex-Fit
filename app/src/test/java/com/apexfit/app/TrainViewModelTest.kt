package com.apexfit.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class TrainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var testDb: AppDatabase? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        com.apexfit.app.di.ServiceLocator.reset()
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
        com.apexfit.app.di.ServiceLocator.reset()
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
        testScheduler.advanceUntilIdle()
        var inserted = dao.getExerciseById("custom-super-press")
        var retries = 0
        while (inserted == null && retries < 30) {
            Thread.sleep(20)
            inserted = dao.getExerciseById("custom-super-press")
            retries++
        }
        assertNotNull("Exercise should be inserted into DB", inserted)
        assertEquals("Custom Super Press", inserted?.name)
        assertEquals("User Created", inserted?.category)
        assertEquals("Chest", inserted?.primaryMuscle)
        
        var metadata = dao.getMetadataForExercise("custom-super-press")
        var metaRetries = 0
        while (metadata == null && metaRetries < 30) {
            Thread.sleep(20)
            metadata = dao.getMetadataForExercise("custom-super-press")
            metaRetries++
        }
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
        testScheduler.advanceUntilIdle()
        var plans = dao.getAllPlans()
        var retries = 0
        while ((plans.find { it.id == 2020L }?.isActive != true) && retries < 30) {
            Thread.sleep(20)
            plans = dao.getAllPlans()
            retries++
        }
        assertTrue("Plan 2020 should be active", plans.find { it.id == 2020L }?.isActive == true)
        assertFalse("Plan 1010 should be inactive", plans.find { it.id == 1010L }?.isActive ?: true)
    }
}
