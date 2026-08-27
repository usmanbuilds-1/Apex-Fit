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
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class HomeViewModelTest {

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
    fun logWeight_coercesWeightToMinimum() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dao = testDb!!.fitnessDao()
        
        val viewModel = HomeViewModel(application)
        
        // Log negative weight
        viewModel.logWeight(-10.0)
        testScheduler.runCurrent()

        var entries = dao.getAllWeightEntries()
        var retries = 0
        while (entries.isEmpty() && retries < 20) {
            testScheduler.runCurrent()
            Thread.sleep(10)
            entries = dao.getAllWeightEntries()
            retries++
        }

        assertFalse("Weight entries should not be empty", entries.isEmpty())
        val entry = entries.first()
        assertEquals("Weight should be coerced to 20.0 kg (minimum limit)", 20.0, entry.weight, 0.001)

        viewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
    }
}
