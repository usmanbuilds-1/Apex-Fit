package com.apexfit.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexfit.app.SessionCommitResult
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.ui.theme.ApexFitTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionCompleteSheetInteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun viewWorkoutOpensDetailsAndSystemBackReturnsToReceipt() {
        var dismissed = false

        compose.setContent {
            ApexFitTheme {
                SessionCompleteSheet(
                    receipt = receipt,
                    sessionSets = sessionSets,
                    units = "kg",
                    onDismiss = { dismissed = true }
                )
            }
        }

        compose.onNodeWithText("View workout").performClick()
        compose.onNodeWithText("Workout details").assertIsDisplayed()
        compose.onNodeWithText("16 kg × 12 · RPE 8 · Rest 1:30").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        compose.onNodeWithText("Upper Body A complete").assertIsDisplayed()
        compose.onNodeWithText("View workout").assertIsDisplayed()
        assertFalse(dismissed)
    }

    @Test
    fun doneInvokesTheReceiptDismissAction() {
        var dismissed = false

        compose.setContent {
            ApexFitTheme {
                SessionCompleteSheet(
                    receipt = receipt,
                    sessionSets = sessionSets,
                    units = "kg",
                    onDismiss = { dismissed = true }
                )
            }
        }

        compose.onNodeWithText("View workout").performClick()
        compose.onNodeWithText("Done").performClick()

        compose.runOnIdle { assertTrue(dismissed) }
    }

    private companion object {
        val receipt = SessionCommitResult(
            sessionId = "interaction-test",
            durationMinutes = 38,
            totalVolumeTonnes = 0.0,
            setsCompleted = 2,
            confirmedPRs = emptyList(),
            sessionType = "Upper Body A"
        )

        val sessionSets = listOf(
            UiExerciseSet(
                weight = 16.0,
                reps = 12,
                rpe = 8,
                exerciseId = "press",
                exerciseName = "Dumbbell Chest Press",
                restTaken = 90
            ),
            UiExerciseSet(
                weight = 14.0,
                reps = 13,
                rpe = 8,
                exerciseId = "press",
                exerciseName = "Dumbbell Chest Press",
                restTaken = 0
            )
        )
    }
}
