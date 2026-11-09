package com.apexfit.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device rendering test, not just a semantics assertion. The prior bug had
 * readable content in the semantics tree but near-black pixels in the image.
 */
@RunWith(AndroidJUnit4::class)
class ScreenBrightnessSmokeTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun trainNutritionAndProgressTitlesAreActuallyBright() {
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithTag("onboarding_explore").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("nav_tab_train").fetchSemanticsNodes().isNotEmpty()
        }
        // A fresh install has no profile. Explore saves onboarding without
        // needing a fake plan, preserving production startup behavior.
        if (compose.onAllNodesWithTag("onboarding_explore").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("onboarding_explore").performClick()
        }
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithTag("nav_tab_train").fetchSemanticsNodes().isNotEmpty()
        }

        assertScreenBrightness("train", "train_content_title")
        assertScreenBrightness("nutrition", "nutrition_content_title")
        assertScreenBrightness("progress", "progress_content_title")
    }

    private fun assertScreenBrightness(route: String, titleTag: String) {
        compose.onNodeWithTag("nav_tab_$route").performClick()
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithTag(titleTag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(titleTag).assertIsDisplayed()

        val screenshot = compose.onNodeWithTag(titleTag).captureToImage().toPixelMap()
        var bright = 0
        var sampled = 0
        for (y in 0 until screenshot.height step 2) {
            for (x in 0 until screenshot.width step 2) {
                val color = screenshot[x, y]
                if (color.red > 0.55f && color.green > 0.55f && color.blue > 0.55f) bright++
                sampled++
            }
        }
        // White title glyphs occupy far more than 1% of their own bounds.
        // A 90-99% black scrim produces essentially zero qualifying pixels.
        assertTrue(
            "$route title pixels are too dark: $bright of $sampled bright samples",
            bright > sampled / 100
        )

        // Save a full app screenshot for inspection in the CI artifact.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(context.filesDir, "render-$route.png")
        out.outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
