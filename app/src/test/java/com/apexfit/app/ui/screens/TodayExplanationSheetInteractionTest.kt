package com.apexfit.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexfit.app.ui.theme.ApexFitTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodayExplanationSheetInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readinessExplanationDismissesFromCloseAction() {
        val isVisible = mutableStateOf(true)
        var dismissed = false

        compose.setContent {
            ApexFitTheme {
                if (isVisible.value) {
                    TodayReadinessExplanationDialog(readiness = null) {
                        dismissed = true
                        isVisible.value = false
                    }
                }
            }
        }

        compose.onNodeWithText("Readiness estimate").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.waitForIdle()

        assertTrue(dismissed)
    }
}
