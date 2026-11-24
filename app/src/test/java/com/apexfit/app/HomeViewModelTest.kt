package com.apexfit.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.viewModelScope
import com.apexfit.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class HomeViewModelTest {

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
        runBlocking {
            DataStoreManager.getInstance(application).clearAllData()
        }
    }

    @After
    fun tearDown() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        runBlocking {
            DataStoreManager.getInstance(application).clearAllData()
        }
        Dispatchers.resetMain()
        testDb?.close()
        testDb = null
        AppDatabase.setTestDatabase(null)
    }

    @Test
    fun logWeight_rejectsInvalidWeight() = runTest(testDispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val db = testDb!!
        val dao = db.fitnessDao()
        val dataStore = DataStoreManager.getInstance(application)
        val repository = com.apexfit.app.data.repository.FitnessRepositoryImpl(db, dao, dataStore)
        
        val viewModel = HomeViewModel(application, repository, dataStore, dao)
        
        // Log negative weight
        val job = viewModel.logWeight(-10.0)
        assertNotNull("logWeight job should not be null", job)
        job?.join()

        assertTrue("Invalid weight must not create a measurement", dao.getWeightEntries().isEmpty())
        assertNotNull(viewModel.weightLogError.value)

        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }

    @Test
    fun backfilledAndDeletedWeightsKeepCurrentProfileOnNewestEntry() = runTest(testDispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val db = testDb!!
        val dao = db.fitnessDao()
        val dataStore = DataStoreManager.getInstance(application)
        dataStore.clearAllData()
        val repository = com.apexfit.app.data.repository.FitnessRepositoryImpl(db, dao, dataStore)
        val viewModel = HomeViewModel(application, repository, dataStore, dao)

        viewModel.logWeight(80.0, date = com.apexfit.app.utils.getTodayDateString())?.join()
        viewModel.logWeight(70.0, date = com.apexfit.app.utils.getDateDaysAgo(1))?.join()

        assertEquals(
            "Backfilling yesterday must not replace today's current weight",
            80.0,
            repository.getCurrentWeightFlow().first(),
            0.001
        )

        val newest = dao.getWeightEntries().first()
        viewModel.deleteWeightById(newest.id).join()

        assertEquals(
            "Deleting the newest entry should fall back to the next newest logged weight",
            70.0,
            repository.getCurrentWeightFlow().first(),
            0.001
        )

        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }


    @Test
    fun multipleSameDaySavesAndEditsPreserveEveryMeasurement() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = testDb!!
        val dao = db.fitnessDao()
        val store = DataStoreManager.getInstance(app)
        val repo = com.apexfit.app.data.repository.FitnessRepositoryImpl(db, dao, store)
        val vm = HomeViewModel(app, repo, store, dao)
        val date = com.apexfit.app.utils.getTodayDateString()
        try {
            vm.logWeight(61.0, date)?.join()
            vm.logWeight(62.0, date)?.join()
            vm.logWeight(63.0 * com.apexfit.app.utils.AppConstants.KG_TO_LBS, date, "lb")?.join()
            val entries = dao.getWeightEntries()
            assertEquals(3, entries.size)
            assertEquals(63.0, repo.getCurrentWeightFlow().first(), 0.00001)
            val newest = entries.first()
            vm.editWeightById(newest.id, 64.0)?.join()
            assertEquals(3, dao.getWeightEntries().size)
            assertEquals(newest.time, dao.getWeightEntryById(newest.id)?.time)
            assertEquals(64.0, repo.getCurrentWeightFlow().first(), 0.00001)
            vm.deleteWeightById(newest.id).join()
            assertEquals(2, dao.getWeightEntries().size)
            assertEquals(62.0, repo.getCurrentWeightFlow().first(), 0.00001)
            vm.logWeight(Double.NaN)?.join()
            vm.logWeight(Double.POSITIVE_INFINITY)?.join()
            assertEquals(2, dao.getWeightEntries().size)
        } finally { vm.viewModelScope.coroutineContext[Job]?.cancelChildren() }
    }

}
