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
        compose.onNodeWithText("Lose fat").assertExists()
        compose.onNodeWithText("Recomposition").assertExists()
        compose.onNodeWithText("Build muscle").assertExists()
        compose.onNodeWithText("Maintain").assertExists()
        compose.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        compose.onNodeWithText("Lose fat").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithText("Personal details").assertIsDisplayed()
        fillProfileDetails()
        compose.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        compose.onNodeWithText("Training regularly").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_frequency_2").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithText("Your plan is ready").assertIsDisplayed()
        compose.onNodeWithText("Full body · 2 days").assertExists()
        compose.onNodeWithText("Starting targets").assertExists()
        compose.runOnIdle { assertNull(submitted) }
        compose.onNodeWithTag("onboarding_complete_button").performClick()
        compose.runOnIdle {
            assertEquals("Lose Fat", submitted?.goal)
            assertEquals("Advanced", submitted?.experience)
            assertEquals(2, submitted?.daysPerWeek)
        }
    }

    @Test
    fun backAndReviewChangesKeepAnswers() {
        compose.setContent { OnboardingScreen(onComplete = {}) }
        compose.onNodeWithTag("onboarding_welcome_next").performScrollTo().performClick()
        compose.onNodeWithText("Recomposition").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        fillProfileDetails()
        compose.onNodeWithText("Finding my rhythm").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_frequency_5").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_back").performClick()
        compose.onNodeWithTag("onboarding_frequency_4").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithText("Upper / Lower · 4 days").assertExists()
        compose.onNodeWithText("Recomposition").assertExists()
    }

    @Test
    fun personalDetailsRequireAdultAge() {
        compose.setContent { OnboardingScreen(onComplete = {}) }
        compose.onNodeWithTag("onboarding_welcome_next").performScrollTo().performClick()
        compose.onNodeWithText("Gain Muscle").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()

        compose.onNodeWithTag("onboarding_name").performScrollTo().performTextInput("Alex")
        compose.onNodeWithTag("onboarding_height").performScrollTo().performTextInput("175")
        compose.onNodeWithTag("onboarding_weight").performScrollTo().performTextInput("70")
        compose.onNodeWithTag("onboarding_age").performScrollTo().performTextInput("17")
        compose.onNodeWithText("Male").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithText("Apex Fit is currently for adults aged 18 to 100.").assertExists()
        compose.onNodeWithText("Personal details").assertExists()

        compose.onNodeWithTag("onboarding_age").performScrollTo().performTextClearance()
        compose.onNodeWithTag("onboarding_age").performTextInput("18")
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithText("What’s your training experience?").assertExists()
    }

    private fun fillProfileDetails() {
        compose.onNodeWithTag("onboarding_name").performScrollTo().performTextInput("Alex")
        compose.onNodeWithTag("onboarding_height").performScrollTo().performTextInput("175")
        compose.onNodeWithTag("onboarding_weight").performScrollTo().performTextInput("70")
        compose.onNodeWithTag("onboarding_age").performScrollTo().performTextInput("30")
        compose.onNodeWithText("Male").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
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
