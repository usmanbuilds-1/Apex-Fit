package com.apexfit.app.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize in-process sensitive snapshots, deletion and scheduled data processing. */
internal object LocalDataGate {
    private val mutex = Mutex()
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
}
