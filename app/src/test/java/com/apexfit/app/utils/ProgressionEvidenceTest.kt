package com.apexfit.app.utils

import com.apexfit.app.data.ActiveExercise
import com.apexfit.app.data.ActiveSet
import com.apexfit.app.data.LastSetWithDate
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ProgressionEvidenceTest {
    private val today = LocalDate.parse("2026-11-28")
    private fun set(n: Int, reps: Int = 12, kg: Double = 20.0, rir: Int? = 2,
                    basis: RecordedLoadBasis? = RecordedLoadBasis.PER_HAND, setup: String = "Gym A") =
        LastSetWithDate(n.toLong(), "s", "press", "Press", "Chest", kg, reps,
            EffortSelection.rpe(rir ?: -1), false, 90, true, "2026-11-26",
            occurrenceIndex = 0, weightUnit = "kg", sourcePlanExerciseId = 5, setNumber = n,
            setObservation = SetObservationCodec.encode(SetObservation(kg, reps, rir, basis,
                if (basis == null) "" else setup)))
    private fun propose(rows: List<LastSetWithDate>) =
        ProgressionEvidence.proposal(rows, 5, 2, 12, 1.0, today)

    @Test fun verifiedPrescriptionOffersIncreaseButDoesNotApplyItAutomatically() {
        val proposal = propose(listOf(set(2), set(1)))!!
        assertEquals(21.0, proposal.suggestedKg, 0.0)
        assertEquals(listOf(1L, 2L), proposal.sourceSetIds)
        val exercise = ActiveExercise("press", "Press", "Chest", mutableListOf(
            ActiveSet(1, 18.0, 8), ActiveSet(2, 18.0, 8)), progressionProposal = proposal)
        assertEquals(18.0, exercise.sets[0].weight, 0.0)
        val applied = ProgressionEvidence.apply(exercise, proposal)!!
        assertEquals(listOf(21.0, 21.0), applied.sets.map { it.weight })
        assertNull(applied.progressionProposal)
        assertNull(applied.sets[0].setObservation)
        assertNull(ProgressionEvidence.apply(applied, proposal))
    }

    @Test fun missingUnratedLegacyAndMixedEvidenceCannotProgress() {
        assertNull(propose(listOf(set(1))))
        assertNull(propose(listOf(set(1), set(3))))
        assertNull(propose(listOf(set(1), set(2, rir = null))))
        assertNull(propose(listOf(set(1), set(2, basis = null))))
        assertNull(propose(listOf(set(1), set(2).copy(setObservation = null))))
        assertNull(propose(listOf(set(1), set(2, setup = "Other"))))
        assertNull(propose(listOf(set(1), set(2, basis = RecordedLoadBasis.TOTAL))))
        assertNull(propose(listOf(set(1), set(2, kg = 22.0))))
        assertNull(propose(listOf(set(1), set(2).copy(rpe = 9))))
    }

    @Test fun differentPrescriptionAdvancedAndFutureHistoryCannotProgress() {
        assertNull(propose(listOf(set(1), set(2).copy(sourcePlanExerciseId = 6))))
        assertNull(propose(listOf(set(1), set(2).copy(occurrenceIndex = 1))))
        assertNull(propose(listOf(set(1), set(2).copy(advancedDetails = "advanced"))))
        assertNull(propose(listOf(set(1), set(2)).map { it.copy(date = "2026-11-29") }))
    }

    @Test fun failedOrHighEffortSetOffersOnlySameLoadNotDeload() {
        assertEquals(20.0, propose(listOf(set(1), set(2, reps = 6)))!!.suggestedKg, 0.0)
        assertEquals(20.0, propose(listOf(set(1), set(2, rir = 0)))!!.suggestedKg, 0.0)
    }

    @Test fun startedWorkoutOrChangedSetCountRejectsProposal() {
        val proposal = propose(listOf(set(1), set(2)))!!
        val exercise = ActiveExercise("press", "Press", "Chest", mutableListOf(
            ActiveSet(1, 20.0, 8, completed = true), ActiveSet(2, 20.0, 8)),
            progressionProposal = proposal)
        assertNull(ProgressionEvidence.apply(exercise, proposal))
        assertNull(ProgressionEvidence.apply(exercise.copy(sets = mutableListOf(ActiveSet(1, 20.0, 8))), proposal))
    }
}
