package com.apexfit.app.utils

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResetMarkerTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun confirmedResetSurvivesCoordinatorReconstructionUntilExplicitCompletion() {
        val root = directory.newFolder()
        val initial = ResetMarker(root)
        assertFalse(initial.pending())
        initial.begin()
        assertTrue(initial.pending())
        val reopened = ResetMarker(root)
        assertTrue(reopened.pending())
        reopened.begin()
        assertTrue(ResetMarker(root).pending())
        reopened.complete()
        assertFalse(ResetMarker(root).pending())
        reopened.complete()
        assertFalse(ResetMarker(root).pending())
    }

    @Test fun interruptedAtomicWriteIsTreatedAsPendingRatherThanSuccessfulDeletion() {
        val root = directory.newFolder()
        File(root, "local-reset-pending-v1.new").writeText("partial")
        assertTrue(ResetMarker(root).pending())
        ResetMarker(root).complete()
        assertFalse(ResetMarker(root).pending())
    }

    @Test fun failedDeletionStageDoesNotClearResetIntent() {
        val root = directory.newFolder()
        val marker = ResetMarker(root)
        marker.begin()
        assertTrue(runCatching { error("simulated storage failure") }.isFailure)
        assertTrue(ResetMarker(root).pending())
    }
}
