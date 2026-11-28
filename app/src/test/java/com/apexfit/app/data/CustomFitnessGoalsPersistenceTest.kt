package com.apexfit.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.utils.*
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
class CustomFitnessGoalsPersistenceTest {
    @Test fun definitionsPersistArchiveAndDeleteWithoutRewritingAndResetClears() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DataStoreManager.getInstance(context)
        try {
            store.clearAllData()
            val today = LocalDate.now()
            val goal = CustomFitnessGoals.create(FitnessGoalSpec(FitnessGoalKind.TRAINING_DAYS, 8.0), today, emptyList())
            store.insertCustomFitnessGoal(goal)
            assertEquals(goal, DataStoreManager(context).customFitnessGoalsFlow.first().single())
            store.archiveCustomFitnessGoal(goal.id)
            assertEquals(goal.copy(archivedOn = today), store.customFitnessGoalsFlow.first().single())
            store.deleteCustomFitnessGoal(goal.id)
            assertTrue(store.customFitnessGoalsFlow.first().isEmpty())
            store.insertCustomFitnessGoal(goal)
            store.clearAllData()
            assertTrue(store.customFitnessGoalsFlow.first().isEmpty())
        } finally {
            store.clearAllData()
        }
    }
}
