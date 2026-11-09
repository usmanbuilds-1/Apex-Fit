package com.apexfit.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.data.repository.FitnessRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NutritionViewModelTest {
    @Test
    fun acceptedFatValueIsSavedWithoutSilentClamping() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val store = DataStoreManager.getInstance(app)
            val repository = FitnessRepositoryImpl(db, dao, store)
            val viewModel = NutritionViewModel(app, repository, store, dao)
            runTest(dispatcher) {
                viewModel.logNutrition(4500, 0.0, 0.0, 450.0, date = "2026-01-01", name = "Test")
                val saved = dao.getNutritionForDateFlow("2026-01-01").first { it.isNotEmpty() }.single()
                assertEquals(450.0, saved.fat, 0.001)
            }
        } finally {
            db.close()
            Dispatchers.resetMain()
        }
    }
}
