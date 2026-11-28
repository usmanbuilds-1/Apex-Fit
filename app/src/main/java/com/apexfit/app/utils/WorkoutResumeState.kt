package com.apexfit.app.utils

import android.content.Context
import android.provider.Settings
import com.apexfit.app.data.ActiveSession
import com.apexfit.app.data.ActiveRestCheckpoint

internal object WorkoutResumeState {
    fun cursor(session: ActiveSession): Int {
        val max = session.exercises.lastIndex.coerceAtLeast(0)
        return session.cursorIndex?.coerceIn(0, max)
            ?: session.exercises.indexOfFirst { ex -> ex.sets.any { !it.completed && !it.isWarmup } }
                .takeIf { it >= 0 } ?: max
    }

    fun advance(session: ActiveSession, index: Int, skipped: Boolean): ActiveSession {
        require(index in session.exercises.indices)
        require(cursor(session) == index)
        if (CircuitExecution.contains(session, index)) return CircuitExecution.advance(session, index, skipped)
        val skippedIndices = session.skippedOccurrenceIndices.orEmpty()
        return session.copy(
            cursorIndex = (index + 1).coerceAtMost(session.exercises.lastIndex),
            skippedOccurrenceIndices = if (skipped) (skippedIndices + index).distinct() else skippedIndices
        )
    }

    fun bootCount(context: Context): Int? = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
    } catch (_: Exception) { null }

    fun validRest(session: ActiveSession, rest: ActiveRestCheckpoint): Boolean =
        rest.startedElapsedMs >= 0 && rest.deadlineElapsedMs >= rest.startedElapsedMs &&
            rest.totalSeconds in 1..3600 &&
            rest.deadlineElapsedMs - rest.startedElapsedMs == rest.totalSeconds * 1000L &&
            session.exercises.firstOrNull { it.executionKey == rest.exerciseKey }
                ?.sets?.getOrNull(rest.setIndex)?.completed == true

    fun sameBoot(rest: ActiveRestCheckpoint, nowElapsedMs: Long, bootCount: Int?, liveInProcess: Boolean = false): Boolean =
        (liveInProcess || (bootCount != null && rest.bootCount == bootCount)) && nowElapsedMs >= rest.startedElapsedMs

    // Expired recovered timers count only the observable interval up to the
    // saved deadline, not an invented period of rest while the app was closed.
    fun observedRestSeconds(rest: ActiveRestCheckpoint, nowElapsedMs: Long, bootCount: Int?, liveInProcess: Boolean = false): Int? =
        if (!sameBoot(rest, nowElapsedMs, bootCount, liveInProcess)) null
        else ((minOf(nowElapsedMs, rest.deadlineElapsedMs) - rest.startedElapsedMs).coerceAtLeast(0) / 1000L)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
