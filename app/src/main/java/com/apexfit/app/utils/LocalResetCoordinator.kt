package com.apexfit.app.utils

import android.content.Context
import android.app.NotificationManager
import android.util.AtomicFile
import androidx.work.WorkManager
import com.apexfit.app.WorkoutSessionManager
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.data.ProgressPhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Marker is outside Room and Preferences DataStore so either store can be cleared safely.
 * Presence alone is the intent: no private information or mutable step index.
 */
internal class ResetMarker(root: File) {
    private val base = File(root, "local-reset-pending-v1")
    private val file = AtomicFile(base)
    fun pending(): Boolean = base.exists() || File(base.path + ".bak").exists() ||
        File(base.path + ".new").exists()
    fun begin() {
        if (pending()) return
        val stream = file.startWrite()
        try {
            stream.write(1)
            stream.fd.sync()
            file.finishWrite(stream)
            check(pending())
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
    fun complete() {
        file.delete()
        check(!pending()) { "Could not clear reset marker" }
    }
}

internal enum class ResetAccess { CHECKING, READY, RESETTING, FAILED }

internal object LocalResetCoordinator {
    private val _access = MutableStateFlow(ResetAccess.CHECKING)
    val access = _access.asStateFlow()
    private val generation = java.util.concurrent.atomic.AtomicLong(0L)
    fun currentGeneration(): Long = generation.get()
    fun permits(context: Context, expectedGeneration: Long): Boolean =
        !pending(context) && generation.get() == expectedGeneration
    private fun marker(context: Context) = ResetMarker(context.noBackupFilesDir)
    fun pending(context: Context): Boolean = marker(context).pending()
    fun readyForTests() { _access.value = ResetAccess.READY }

    /** Caller holds LocalDataGate; manager locks are obtained before the marker is written. */
    suspend fun resetLocked(context: Context, manager: WorkoutSessionManager) {
        val app = context.applicationContext
        val marker = marker(app)
        try {
            manager.resetWorkoutData(beforeDelete = {
                marker.begin()
                generation.incrementAndGet()
                _access.value = ResetAccess.RESETTING
            }) {
                // Manager's NonCancellable region includes the entire destructive sequence.
                WorkManager.getInstance(app).cancelAllWork().result.get()
                app.getSystemService(NotificationManager::class.java)?.cancelAll()
                SecureUserExport.clearStaging(app)
                ProgressPhotoStore(app).clear()
                AppDatabase.getDatabase(app).clearAllTables()
                DataStoreManager.getInstance(app).clearAllData()
                SeedService.seed(app)
                app.getSystemService(NotificationManager::class.java)?.cancelAll()
                marker.complete()
            }
            _access.value = ResetAccess.READY
            CoachingScheduler.schedule6AmDailyCoachingTask(app)
        } catch (e: Exception) {
            _access.value = if (marker.pending()) ResetAccess.FAILED else ResetAccess.READY
            throw e
        }
    }

    /** Do not construct the workout manager or show main content until a pending wipe is resolved. */
    suspend fun recoverLocked(context: Context, manager: () -> WorkoutSessionManager): Boolean =
        withContext(Dispatchers.IO + NonCancellable) {
            if (!pending(context)) {
                _access.value = ResetAccess.READY
                return@withContext true
            }
            _access.value = ResetAccess.RESETTING
            try {
                resetLocked(context, manager())
                true
            } catch (_: Exception) {
                _access.value = ResetAccess.FAILED
                false
            }
        }
}
