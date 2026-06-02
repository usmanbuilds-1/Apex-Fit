package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.ApexFitApp
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("APEX FIT", appName)
  }

  @Test
  fun `compose apex fit app successfully`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ApexFitApp()
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun `launch main activity successfully`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
    controller.setup()
  }

  @Test
  fun `render onboarded progress screen with heatmap and hypertrophy score index successfully`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = context.applicationContext as android.app.Application
    val fitnessVm = com.example.FitnessViewModel(app)
    val algoVm = com.example.data.AlgorithmViewModel(app)

    // Set onboarded state
    fitnessVm.completeOnboarding("Athlete", "Gain Muscle", 85.0, 90.0, "AI_KEY_TEST")
    fitnessVm.selectTab(3) // Progress Tab

    composeTestRule.setContent {
      MyApplicationTheme {
        ApexFitApp(fitnessViewModel = fitnessVm, algorithmViewModel = algoVm)
      }
    }
    // Verify it doesn't crash during drawing/measurement phases
    composeTestRule.waitForIdle()
  }

  @Test
  fun `test active session set update`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = context.applicationContext as android.app.Application
    val fitnessVm = com.example.FitnessViewModel(app)

    // Start active session
    val planSession = com.example.data.PlanSession(0L, 0L, "Day 1", "Monday", "Push Day")
    val planEx = listOf(
        com.example.data.PlanExercise(1L, 0L, "Bench Press", "Chest", 3, 8, 12, 100.0, 90, "")
    )

    fitnessVm.sessionManager.startSession(planSession, planEx, 80.0, 180.0, "kg")

    // Log set complete
    fitnessVm.logWorkoutSetState(1L, 0, 100.0, 10, 8, true)

    val active = fitnessVm.sessionManager.activeSession.value
    org.junit.Assert.assertNotNull(active)
    org.junit.Assert.assertTrue(active!!.exercises[0].sets[0].completed)
  }
}
