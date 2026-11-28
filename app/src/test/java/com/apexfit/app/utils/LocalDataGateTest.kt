package com.apexfit.app.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalDataGateTest {
    @Test fun snapshotAndResetCannotRunAtSameTime() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val export = launch {
            LocalDataGate.exclusive {
                events += "snapshot-start"
                entered.complete(Unit)
                release.await()
                events += "snapshot-end"
            }
        }
        entered.await()
        val reset = launch { LocalDataGate.exclusive { events += "reset" } }
        runCurrent()
        assertEquals(listOf("snapshot-start"), events)
        release.complete(Unit)
        export.join()
        reset.join()
        assertEquals(listOf("snapshot-start", "snapshot-end", "reset"), events)
    }

    @Test fun cancelledQueuedJobDoesNotEnterAndFailureReleasesGate() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val active = launch { LocalDataGate.exclusive { entered.complete(Unit); release.await() } }
        entered.await()
        var ran = false
        val queued = launch { LocalDataGate.exclusive { ran = true } }
        runCurrent()
        queued.cancelAndJoin()
        release.complete(Unit)
        active.join()
        assertFalse(ran)
        assertTrue(runCatching { LocalDataGate.exclusive { error("expected") } }.isFailure)
        assertEquals(7, async { LocalDataGate.exclusive { 7 } }.await())
    }
}
