package com.apexfit.app

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
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
class TrainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var testDb: AppDatabase? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        com.apexfit.app.di.ServiceLocator.reset()
        val application = ApplicationProvider.getApplicationContext<Application>()
        com.apexfit.app.di.ServiceLocator.setAppScope(kotlinx.coroutines.CoroutineScope(testDispatcher), application)
        val db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testDb = db
        AppDatabase.setTestDatabase(db)
        com.apexfit.app.di.ServiceLocator.setDatabase(db)
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
    fun addCustomExercise_insertsExerciseRowWhenNameIsNew() = runTest(testDispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dao = testDb!!.fitnessDao()

        dao.insertWorkoutPlan(
            WorkoutPlan(
                id = 1L,
                name = "Test Plan",
                goal = "General",
                isActive = true,
                createdAt = System.currentTimeMillis()
            )
        )
        
        val viewModel = TrainViewModel(application)
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
        
        viewModel.addCustomExercise(planExercise).join()
        testScheduler.advanceUntilIdle()

        val inserted = dao.getExerciseById("custom-super-press")
        assertNotNull("Exercise should be inserted into DB", inserted)
        assertEquals("Custom Super Press", inserted?.name)
        assertEquals("User Created", inserted?.category)
        assertEquals("Chest", inserted?.primaryMuscle)
        
        val metadata = dao.getMetadataForExercise("custom-super-press")
        assertNotNull("ExerciseMetadata should also be inserted", metadata)

        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }

    @Test
    fun activatePlan_updatesActiveStatus() = runTest(testDispatcher) {
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

        viewModel.activatePlan(2020L).join()
        testScheduler.advanceUntilIdle()
        val plans = dao.getAllPlans()

        assertTrue("Plan 2020 should be active", plans.find { it.id == 2020L }?.isActive == true)
        assertFalse("Plan 1010 should be inactive", plans.find { it.id == 1010L }?.isActive ?: true)

        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }
}
