package com.apexfit.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.apexfit.app.data.OnboardingProfile
import com.apexfit.app.ui.screens.OnboardingScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun referenceFlowSavesOnlyOnFinalActionAndSupportsTwoDays() {
        var submitted: OnboardingProfile? = null
        compose.setContent { OnboardingScreen(onComplete = { submitted = it }) }
        compose.onNodeWithTag("onboarding_welcome_next").performScrollTo().performClick()
        compose.onNodeWithText("Losing").assertExists()
        compose.onNodeWithText("Recomp").assertExists()
        compose.onNodeWithText("Gaining").assertExists()
        compose.onNodeWithText("Losing").performScrollTo().performClick()
        compose.onNodeWithText("Meet yourself where you are.").assertIsDisplayed()
        compose.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        compose.onNodeWithText("Training regularly").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_frequency_2").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithText("Enter a name of 1–50 characters.").assertExists()
        fillDetails()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithText("Your profile, ready to go.").assertIsDisplayed()
        compose.onNodeWithText("2 training days per week").assertExists()
        compose.runOnIdle { assertNull(submitted) }
        compose.onNodeWithTag("onboarding_complete_button").performClick()
        compose.runOnIdle {
            assertEquals("Lose Fat", submitted?.goal)
            assertEquals("Advanced", submitted?.experience)
            assertEquals(2, submitted?.daysPerWeek)
            assertEquals("Sam", submitted?.name)
            assertEquals(180.0, submitted!!.heightCm, 0.001)
            assertEquals(80.0, submitted!!.weightKg, 0.001)
            assertEquals(28, submitted?.age)
            assertEquals("female", submitted?.sex)
        }
    }

    @Test
    fun backAndReviewChangesKeepAnswers() {
        compose.setContent { OnboardingScreen(onComplete = {}) }
        compose.onNodeWithTag("onboarding_welcome_next").performScrollTo().performClick()
        compose.onNodeWithText("Recomp").performScrollTo().performClick()
        compose.onNodeWithText("Finding my rhythm").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_frequency_5").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        fillDetails()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_change_Your details").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_name").assertTextContains("Sam")
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_change_Activity").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_frequency_4").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithText("4 training days per week").assertExists()
        compose.onNodeWithText("Finding my rhythm").assertExists()
        compose.onNodeWithText("Recomp").assertExists()
    }

    private fun fillDetails() {
        compose.onNodeWithTag("onboarding_name").performScrollTo().performTextInput("Sam")
        compose.onNodeWithTag("onboarding_height").performScrollTo().performTextInput("180")
        compose.onNodeWithTag("onboarding_weight").performScrollTo().performTextInput("80")
        compose.onNodeWithTag("onboarding_age").performScrollTo().performTextInput("28")
        compose.onNodeWithText("Female", useUnmergedTree = true).performScrollTo().performClick()
    }

    @Test
    fun imperialDetailsAreSubmittedAsCanonicalMeasurements() {
        var submitted: OnboardingProfile? = null
        compose.setContent { OnboardingScreen(preferredUnits = "lb", onComplete = { submitted = it }) }
        compose.onNodeWithTag("onboarding_welcome_next").performScrollTo().performClick()
        compose.onNodeWithText("Gaining").performScrollTo().performClick()
        compose.onNodeWithText("New to training").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_name").performScrollTo().performTextInput("Alex")
        compose.onNodeWithTag("onboarding_height").performScrollTo().performTextInput("70")
        compose.onNodeWithTag("onboarding_weight").performScrollTo().performTextInput("176.37")
        compose.onNodeWithTag("onboarding_age").performScrollTo().performTextInput("30")
        compose.onNodeWithText("Male", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_complete_button").performClick()
        compose.runOnIdle {
            assertEquals(177.8, submitted!!.heightCm, 0.001)
            assertEquals(80.0, submitted!!.weightKg, 0.01)
            assertEquals("lb", submitted!!.units)
        }
    }

    @Test
    fun exploreDoesNotSubmitInventedProfile() {
        var explored = false
        var submitted = false
        compose.setContent { OnboardingScreen(onExplore = { explored = true }, onComplete = { submitted = true }) }
        compose.onNodeWithTag("onboarding_explore").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(explored); assertFalse(submitted) }
    }
}
