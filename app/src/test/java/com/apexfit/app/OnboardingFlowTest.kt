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
        compose.onNodeWithText("Your profile, ready to go.").assertIsDisplayed()
        compose.onNodeWithText("2 training days per week").assertExists()
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
        compose.onNodeWithText("Recomp").performScrollTo().performClick()
        compose.onNodeWithText("Finding my rhythm").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_frequency_5").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding_change_Activity").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_frequency_4").performClick()
        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithText("4 training days per week").assertExists()
        compose.onNodeWithText("Finding my rhythm").assertExists()
        compose.onNodeWithText("Recomp").assertExists()
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
