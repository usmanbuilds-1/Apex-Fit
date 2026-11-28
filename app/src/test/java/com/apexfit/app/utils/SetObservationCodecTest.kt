package com.apexfit.app.utils

import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.ActiveSession
import com.apexfit.app.data.ActiveExercise
import com.apexfit.app.data.ActiveSet
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class SetObservationCodecTest {
    private val evidence = SetObservation(20.0, 5, 0, RecordedLoadBasis.TOTAL, "Bench A", true)
    private val set = ExerciseSet(id = 1, sessionId = "s", exerciseId = "bench", exerciseName = "Bench",
        muscleGroup = "Chest", weight = 20.0, reps = 5, rpe = 10, isWarmup = false, completed = true,
        setObservation = SetObservationCodec.encode(evidence))

    @Test fun preservesExplicitZeroAndUnknownEffort() {
        assertEquals(evidence, SetObservationCodec.match(set.setObservation, 20.0, 5))
        val unknown = SetObservation(20.0, 5, null, null)
        assertEquals(unknown, SetObservationCodec.match(SetObservationCodec.encode(unknown), 20.0, 5))
        assertNull(SetObservationCodec.match(null, 20.0, 5))
    }

    @Test fun correctionInvalidatesCapturedLoadAndRestOnlyDoesNot() {
        assertNotNull(SetObservationCodec.confirmation(set))
        assertNotNull(SetObservationCodec.confirmation(set.copy(restTaken = 60)))
        assertNull(SetObservationCodec.confirmation(set.copy(reps = 6)))
        assertNull(SetObservationCodec.confirmation(set.copy(weight = 21.0)))
        assertNull(SetObservationCodec.confirmation(set.copy(isWarmup = true)))
        assertFalse(SetObservationCodec.confirmation(set.copy(rpe = 8))!!.freeWeightRepLimit)
    }

    @Test fun manualOverrideRequiresMatchingFingerprint() {
        val captured = SetObservationCodec.confirmation(set)!!
        val observation = StrengthObservation(1L, 20.0, 5, captured.fingerprint, capturedConfirmation = captured)
        val override = captured.copy(basis = RecordedLoadBasis.PER_HAND, setup = "Other", freeWeightRepLimit = false)
        assertEquals(captured, LoadConfirmations.match(observation, emptyMap()))
        assertEquals(override, LoadConfirmations.match(observation, mapOf(1L to override)))
        assertEquals(captured, LoadConfirmations.match(observation, mapOf(1L to override.copy(fingerprint = "old"))))
    }

    @Test fun repLimitRequiresExplicitZeroRirTotalFreeWeightAndEligibleReps() {
        assertFalse(SetObservationCodec.valid(evidence.copy(rir = null)))
        assertFalse(SetObservationCodec.valid(evidence.copy(basis = RecordedLoadBasis.PER_HAND)))
        assertFalse(SetObservationCodec.valid(evidence.copy(reps = 6)))
        assertFalse(SetObservationCodec.valid(evidence.copy(setup = "bad|name")))
    }

    @Test fun draftRoundTripAndLegacyUpgradePreserveSessionIdentity() {
        val active = ActiveSet(1, 20.0, 5, rpe = 10, completed = true, setObservation = set.setObservation)
        val session = ActiveSession("1", "Upper", exercises = mutableListOf(
            ActiveExercise("bench", "Bench", "Chest", mutableListOf(active))))
        assertEquals(set.setObservation, ActiveWorkoutCodec.decode(Gson().toJson(session)).session.exercises.single().sets.single().setObservation)
        val old = session.copy(schemaVersion = 5, exercises = mutableListOf(
            session.exercises.single().copy(sets = mutableListOf(active.copy(setObservation = null)))))
        val restored = ActiveWorkoutCodec.decode(Gson().toJson(old)).session
        assertEquals(old.sessionId, restored.sessionId)
        assertNull(restored.exercises.single().sets.single().setObservation)
    }
}
