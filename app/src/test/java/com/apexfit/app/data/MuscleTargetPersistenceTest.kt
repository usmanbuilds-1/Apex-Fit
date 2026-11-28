package com.apexfit.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.utils.MuscleTargetHistory
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MuscleTargetPersistenceTest {
    @Test
    fun nextWeekTargetPersistsIsEditableAndResetClearsHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DataStoreManager.getInstance(context)
        try {
            store.clearAllData()
            val today = LocalDate.now()
            val nextWeek = MuscleTargetHistory.nextWeek(today)
            store.scheduleMuscleTarget("Chest", 8, nextWeek)
            store.scheduleMuscleTarget("Biceps", 0, nextWeek)
            store.scheduleMuscleTarget("Chest", 10, nextWeek)
            assertTrue(runCatching { store.scheduleMuscleTarget("Chest", 5, MuscleTargetHistory.week(today)) }.isFailure)
            val history = DataStoreManager(context).muscleTargetHistoryFlow.first()
            assertEquals(2, history.size)
            assertNull(MuscleTargetHistory.resolve(history, "chest", today))
            assertEquals(10, MuscleTargetHistory.resolve(history, "chest", nextWeek)!!.target)
            assertEquals(0, MuscleTargetHistory.resolve(history, "biceps", nextWeek)!!.target)
            store.clearAllData()
            assertTrue(store.muscleTargetHistoryFlow.first().isEmpty())
        } finally {
            store.clearAllData()
        }
    }
}
