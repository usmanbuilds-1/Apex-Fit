package com.apexfit.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.utils.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetLoadConfirmationPersistenceTest {
    @Test fun confirmationPersistsCanBeClearedAndResetRemovesAll() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DataStoreManager.getInstance(context)
        try {
            store.clearAllData()
            val c = SetLoadConfirmation(123, "a".repeat(64), RecordedLoadBasis.TOTAL, "Gym A")
            store.confirmSetLoad(c.setId, c)
            assertEquals(c, DataStoreManager(context).setLoadConfirmationsFlow.first()[123L])
            store.confirmSetLoad(c.setId, c.copy(basis = RecordedLoadBasis.PER_HAND))
            assertEquals(RecordedLoadBasis.PER_HAND, store.setLoadConfirmationsFlow.first()[123L]?.basis)
            store.confirmSetLoad(c.setId, null)
            assertTrue(store.setLoadConfirmationsFlow.first().isEmpty())
            store.confirmSetLoad(c.setId, c)
            store.clearAllData()
            assertTrue(store.setLoadConfirmationsFlow.first().isEmpty())
        } finally {
            store.clearAllData()
        }
    }
}
