package com.apexfit.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexfit.app.ui.theme.ApexFitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeightLogDialogInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun saveFailureRemainsVisibleAndAllowsRetry() {
        var savedWeight: Double? = null
        compose.setContent {
            ApexFitTheme {
                LogWeightDialog(
                    initialInput = "78.4",
                    units = "kg",
                    onDismiss = {},
                    onSave = { savedWeight = it },
                    saveError = "Failed to save weight. Please try again."
                )
            }
        }

        compose.onNodeWithText("Failed to save weight. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("SAVE").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(78.4, savedWeight!!, 0.001) }
    }

    @Test
    fun savingStateDisablesDuplicateSubmission() {
        var saved = false
        compose.setContent {
            ApexFitTheme {
                LogWeightDialog(
                    initialInput = "78.4",
                    units = "kg",
                    onDismiss = {},
                    onSave = { saved = true },
                    isSaving = true
                )
            }
        }

        compose.onNodeWithText("SAVING…").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("CANCEL").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(false, saved) }
    }
}
