package com.apexfit.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.apexfit.app.ui.models.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApexDirectScreenHostTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun allTabsRenderAndSaveableStateSurvivesSwitches() {
        compose.setContent {
            var route by remember { mutableStateOf(TopLevelDestination.HOME.route) }
            val stateHolder = rememberSaveableStateHolder()
            Column {
                TopLevelDestination.entries.forEach { tab ->
                    Button(
                        modifier = Modifier.testTag("switch_${tab.route}"),
                        onClick = { route = tab.route }
                    ) { Text("Switch to ${tab.label}") }
                }
                ApexDirectScreenHost(route, stateHolder) { screen ->
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    Column {
                        Text("Visible screen: $screen")
                        Text("Count: $count")
                        Button(
                            modifier = Modifier.testTag("increment"),
                            onClick = { count++ }
                        ) { Text("Increment") }
                    }
                }
            }
        }

        compose.onNodeWithText("Visible screen: home").assertIsDisplayed()
        compose.onNodeWithTag("increment").performClick()
        compose.onNodeWithText("Count: 1").assertExists()

        for (tab in TopLevelDestination.entries.drop(1)) {
            compose.onNodeWithTag("switch_${tab.route}").performClick()
            compose.onNodeWithText("Visible screen: ${tab.route}").assertIsDisplayed()
            compose.onNodeWithText("Count: 0").assertExists()
        }
        compose.onNodeWithTag("switch_home").performClick()
        compose.onNodeWithText("Visible screen: home").assertIsDisplayed()
        compose.onNodeWithText("Count: 1").assertExists()
    }

    @Test fun invalidTopLevelRoutesCannotBeSelected() {
        assertEquals(null, ApexScreenRoutes.tab(-1))
        assertEquals(null, ApexScreenRoutes.tab(999))
        assertEquals("home", ApexScreenRoutes.safeReturn(ApexScreenRoutes.SETTINGS))
        assertEquals("train", ApexScreenRoutes.safeReturn("train"))
    }
}
