package com.apexfit.app

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    private var testScope: kotlinx.coroutines.CoroutineScope? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val application = ApplicationProvider.getApplicationContext<Application>()
        val scope = kotlinx.coroutines.CoroutineScope(testDispatcher + kotlinx.coroutines.SupervisorJob())
        testScope = scope
        kotlinx.coroutines.runBlocking {
            DataStoreManager.getInstance(application).saveActiveSessionJson(null)
        }
        val db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testDb = db
        AppDatabase.setTestDatabase(db)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        testScope?.cancel()
        testScope = null
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
        testDb?.close()
        testDb = null
        AppDatabase.setTestDatabase(null)
        kotlinx.coroutines.runBlocking {
            DataStoreManager.getInstance(application).saveActiveSessionJson(null)
        }
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
        
        val dataStore = DataStoreManager.getInstance(application)
        val repository = com.apexfit.app.data.repository.FitnessRepositoryImpl(testDb!!, dao, dataStore)
        val viewModel = PlanBuilderViewModel(application, repository, dataStore, dao)
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
        
        val job = viewModel.addCustomExercise(planExercise)
        job.join()
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
    fun completingSetKeepsSelectedRpeAndMovesToNextExercise() = runTest(testDispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dao = testDb!!.fitnessDao()
        val store = DataStoreManager.getInstance(application)
        val repository = com.apexfit.app.data.repository.FitnessRepositoryImpl(testDb!!, dao, store)
        val manager = WorkoutSessionManager(repository, store, testScope!!, dao, application)
        val viewModel = WorkoutExecutionViewModel(application, repository, store, dao, manager)
        val first = PlanExercise(planSessionId = 1, exerciseId = "bench", name = "Bench", muscleGroup = "Chest",
            sets = 1, repsMin = 8, repsMax = 12, weight = 10.0, restSeconds = 120, notes = "")
        val second = first.copy(id = 2, exerciseId = "row", name = "Row", muscleGroup = "Back")
        manager.startSession(PlanSession(id = 1, planId = 1, label = "Upper", day = "Monday", focus = "Upper"),
            listOf(first, second), 70.0, 175.0, "kg")
        var restCalls = 0
        viewModel.completeSetWithRpe("bench", "Bench", "Chest", 0, 10.0, 10, 1, 9) { restCalls++ }?.join()
        assertEquals(9, manager.activeSession.value!!.exercises[0].sets[0].rpe)
        assertEquals(1, viewModel.currentExerciseIdx.value)
        assertEquals(1, restCalls)
        viewModel.completeSetWithRpe("row", "Row", "Back", 0, 10.0, 10, 1, 8) { restCalls++ }?.join()
        assertEquals("Final set should not start another timer", 1, restCalls)
        manager.discardAndExit()
        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }

    @Test
    fun rirConfirmationLogsSelectedEffortAndStartsRestWithSetContext() = runTest(testDispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dao = testDb!!.fitnessDao()
        val store = DataStoreManager.getInstance(application)
        val repository = com.apexfit.app.data.repository.FitnessRepositoryImpl(testDb!!, dao, store)
        val manager = WorkoutSessionManager(repository, store, testScope!!, dao, application)
        val viewModel = WorkoutExecutionViewModel(application, repository, store, dao, manager)
        val exercise = PlanExercise(
            planSessionId = 1,
            exerciseId = "bench",
            name = "Bench Press",
            muscleGroup = "Chest",
            sets = 2,
            repsMin = 8,
            repsMax = 12,
            weight = 60.0,
            restSeconds = 90,
            notes = ""
        )
        manager.startSession(
            PlanSession(id = 1, planId = 1, label = "Upper", day = "Monday", focus = "Upper"),
            listOf(exercise), 70.0, 175.0, "kg"
        )

        viewModel.openRirSelector("bench", "Bench Press", "Chest", 0, 60.0, 10, 2)
        viewModel.selectRirOption(3)
        var callback: List<Any>? = null
        viewModel.confirmRirAndNextSet { rpe, name, muscle, weight, reps, setIndex, totalSets ->
            callback = listOf(rpe, name, muscle, weight, reps, setIndex, totalSets)
        }?.join()

        val loggedSet = manager.activeSession.value!!.exercises.single().sets.first()
        assertTrue("RIR confirmation should complete the set", loggedSet.completed)
        assertEquals(3, loggedSet.repsInReserve)
        assertEquals(7, loggedSet.rpe)
        assertEquals(listOf(7, "Bench Press", "Chest", 60.0, 10, 0, 2), callback)
        assertTrue("The combined RIR/rest screen stays visible while resting", viewModel.rirOverlayState.value.isVisible)
        assertTrue(viewModel.rirOverlayState.value.hasLoggedSet)
        viewModel.selectRirOption(1)
        val adjustedSet = manager.activeSession.value!!.exercises.single().sets.first()
        assertEquals(1, adjustedSet.repsInReserve)
        assertEquals(9, adjustedSet.rpe)
        viewModel.closeRirSelector()
        manager.discardAndExit()
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

        val dataStore = DataStoreManager.getInstance(application)
        val repository = com.apexfit.app.data.repository.FitnessRepositoryImpl(testDb!!, dao, dataStore)
        val viewModel = PlanBuilderViewModel(application, repository, dataStore, dao)
        testScheduler.advanceUntilIdle()

        viewModel.activatePlan(2020L).join()
        testScheduler.advanceUntilIdle()
        val plans = dao.getAllPlans()

        assertTrue("Plan 2020 should be active", plans.find { it.id == 2020L }?.isActive == true)
        assertFalse("Plan 1010 should be inactive", plans.find { it.id == 1010L }?.isActive ?: true)

        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }

    @Test
    fun substituteScheduledExercisePersistsReplacementAndKeepsRepPrescription() = runTest(testDispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dao = testDb!!.fitnessDao()
        val originalExercise = Exercise(
            id = "dumbbell-lateral-raise", name = "Dumbbell Lateral Raise", category = "Dumbbell",
            primaryMuscle = "Shoulders", equipmentRequired = "Dumbbell"
        )
        val replacementExercise = Exercise(
            id = "cable-lateral-raise", name = "Cable Lateral Raise", category = "Cable",
            primaryMuscle = "Shoulders", equipmentRequired = "Cable"
        )
        dao.insertExercises(listOf(originalExercise, replacementExercise))
        dao.insertExerciseMetadata(ExerciseMetadata(exerciseId = replacementExercise.id, defaultRestSeconds = 75))
        val planId = dao.insertWorkoutPlan(WorkoutPlan(name = "Test Plan", goal = "Gain Muscle", isActive = true))
        val sessionId = dao.insertPlanSession(PlanSession(
            planId = planId, label = "Upper A", day = "Monday", focus = "Shoulders"
        ))
        dao.insertPlanExercise(PlanExercise(
            planSessionId = sessionId,
            exerciseId = originalExercise.id,
            name = originalExercise.name,
            muscleGroup = originalExercise.primaryMuscle,
            sets = 4,
            repsMin = 8,
            repsMax = 12,
            weight = 22.5,
            restSeconds = 120,
            notes = "Use controlled reps."
        ))
        val originalRow = dao.getExercisesForSession(sessionId).single()
        val store = DataStoreManager.getInstance(application)
        val repository = com.apexfit.app.data.repository.FitnessRepositoryImpl(testDb!!, dao, store)
        val viewModel = PlanBuilderViewModel(application, repository, store, dao)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.substituteScheduledExercise(originalRow, replacementExercise))
        val saved = dao.getExercisesForSession(sessionId).single()
        assertEquals(replacementExercise.id, saved.exerciseId)
        assertEquals(replacementExercise.name, saved.name)
        assertEquals(replacementExercise.primaryMuscle, saved.muscleGroup)
        assertEquals(4, saved.sets)
        assertEquals(8, saved.repsMin)
        assertEquals(12, saved.repsMax)
        assertEquals(0.0, saved.weight, 0.0)
        assertEquals(75, saved.restSeconds)
        assertEquals("", saved.notes)
        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }

    @Test
    fun defaultPlanDoesNotReplaceExistingPlan() = runTest(testDispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dao = testDb!!.fitnessDao()
        val existingId = dao.insertWorkoutPlan(WorkoutPlan(
            name = "My plan", goal = "General", isActive = true, createdAt = 1L
        ))
        val store = DataStoreManager.getInstance(application)
        val repo = com.apexfit.app.data.repository.FitnessRepositoryImpl(testDb!!, dao, store)
        val viewModel = PlanBuilderViewModel(application, repo, store, dao)

        viewModel.seedDefaultWorkoutPlan()

        val plans = dao.getAllPlans()
        assertEquals(1, plans.size)
        assertEquals(existingId, plans.single().id)
        assertEquals("My plan", plans.single().name)
        assertTrue(plans.single().isActive)
        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }

    @Test
    fun defaultPlanIsSeededOnceWithNoFixedLoads() = runTest(testDispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dao = testDb!!.fitnessDao()
        val store = DataStoreManager.getInstance(application)
        val repo = com.apexfit.app.data.repository.FitnessRepositoryImpl(testDb!!, dao, store)
        val viewModel = PlanBuilderViewModel(application, repo, store, dao)

        viewModel.seedDefaultWorkoutPlan()
        viewModel.seedDefaultWorkoutPlan()

        val plan = dao.getAllPlans().single()
        assertEquals("Scientific Hypertrophy Split", plan.name)
        assertTrue(plan.isActive)
        val sessions = dao.getSessionsForPlan(plan.id)
        assertEquals(4, sessions.size)
        val exercises = sessions.flatMap { dao.getExercisesForSession(it.id) }
        assertEquals(20, exercises.size)
        assertTrue(exercises.all { it.weight == 0.0 })
        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }

}
