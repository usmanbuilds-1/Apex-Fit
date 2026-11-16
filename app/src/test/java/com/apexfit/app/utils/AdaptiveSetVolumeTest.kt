package com.apexfit.app.utils

import com.apexfit.app.data.ActiveSet
import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.RichTrainingSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AdaptiveSetVolumeTest {
    private val today = LocalDate.parse("2026-11-16")
    private val clean = listOf(exposure(12), exposure(10), exposure(8))

    private fun exposure(day: Int, rpe: Int = 7, warmup: Boolean = false): RichTrainingSession {
        val sets = List(3) { ExerciseSet(weight = 20.0, reps = 8, rpe = rpe, isWarmup = warmup) }
        return RichTrainingSession("2026-11-%02d".format(day), "Pull", true,
            exercises = listOf(ExerciseLog("row", "Row", "Back", sets)))
    }

    private fun adjust(
        sessions: List<RichTrainingSession> = clean, goal: String = "Gain Muscle", readiness: Int? = 80,
        loadChanged: Boolean = false
    ) = AdaptiveSetVolume.adjustment(goal, "row", 3, 8, sessions, today, readiness, loadChanged)

    @Test fun repeatedEvidenceCanAddOnlyOneSet() {
        val adjustment = adjust()!!
        assertEquals(3, adjustment.baseSets)
        assertEquals(4, adjustment.adjustedSets)
    }

    @Test fun sparseDuplicateAndWarmUpEvidenceDoNotChangeThePlan() {
        assertNull(adjust(clean.take(2)))
        assertNull(adjust(listOf(exposure(12), exposure(12), exposure(12))))
        assertNull(adjust(listOf(exposure(12, warmup = true), exposure(10), exposure(8))))
    }

    @Test fun recoveryGoalAndConcurrentLoadChangesGateIncreases() {
        assertNull(adjust(readiness = null))
        assertNull(adjust(readiness = 60))
        assertNull(adjust(goal = "Maintain"))
        assertNull(adjust(loadChanged = true))
    }

    @Test fun lowRecoveryAndRepeatedHighEffortReduceWork() {
        val mixed = listOf(exposure(12, 9), exposure(10, 10), exposure(8))
        assertEquals(2, adjust(mixed, readiness = 45)?.adjustedSets)
        assertNull(adjust(listOf(exposure(12, 9), exposure(10), exposure(8)), readiness = 45))
    }

    @Test fun revertingPreservesLoggedSetsAndRestoresOriginalCountBeforeLogging() {
        val added = SetVolumeAdjustment(3, 4, "Repeated evidence")
        val sets = List(4) { ActiveSet(it + 1, 20.0, 8) }
        assertEquals(sets.take(3), added.revert(sets))
        assertNull(added.revert(sets.mapIndexed { index, set -> set.copy(completed = index == 0) }))
        val reduced = SetVolumeAdjustment(3, 2, "Recovery")
        val restored = reduced.revert(sets.take(2))!!
        assertEquals(sets.take(3), restored)
    }
}
