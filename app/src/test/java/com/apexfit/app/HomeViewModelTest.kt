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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDb?.close()
        testDb = null
        AppDatabase.setTestDatabase(null)
    }

    @Test
    fun logWeight_coercesWeightToMinimum() = runTest(testDispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val db = testDb!!
        val dao = db.fitnessDao()
        val dataStore = DataStoreManager.getInstance(application)
        val repository = com.apexfit.app.data.repository.FitnessRepositoryImpl(db, dao, dataStore)
        
        val viewModel = HomeViewModel(application, repository, dataStore)
        
        // Log negative weight
        val job = viewModel.logWeight(-10.0)
        assertNotNull("logWeight job should not be null", job)
        job?.join()

        val entries = dao.getWeightEntries()
        assertFalse("Weight entries should not be empty", entries.isEmpty())
        val entry = entries.first()
        assertEquals("Weight should be coerced to 20.0 kg (minimum limit)", 20.0, entry.weight, 0.001)

        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }
}
