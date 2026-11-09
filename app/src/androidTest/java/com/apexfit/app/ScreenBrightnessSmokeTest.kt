package com.apexfit.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

        val results = listOf(
            "train" to "train_title_container",
            "nutrition" to "nutrition_title_container",
            "progress" to "progress_title_container"
        ).associate { (route, title) -> route to checkScreenBrightness(route, title) }
        assertTrue(
            "Content contrast failed with reference controls: " +
                results.mapValues { it.value.second },
            results.values.all { it.first }
        )
    }

    private fun checkScreenBrightness(route: String, titleTag: String): Pair<Boolean, String> {
        compose.onNodeWithTag("nav_tab_$route").performClick()
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithTag(titleTag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(titleTag).assertIsDisplayed()

        // Save the full image BEFORE any assertion, so failed runs still produce
        // evidence instead of hiding the screenshot behind a test exception.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(context.filesDir, "render-$route.png")
        out.outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }

        fun brightSamples(tag: String): Pair<Int, Int> {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            var bright = 0
            var samples = 0
            for (y in 0 until pixels.height step 2) {
                for (x in 0 until pixels.width step 2) {
                    val color = pixels[x, y]
                    if (color.red > 0.55f && color.green > 0.55f && color.blue > 0.55f) bright++
                    samples++
                }
            }
            return bright to samples
        }
        val (titleBright, titleSamples) = brightSamples(titleTag)
        val (headerBright, headerSamples) = brightSamples("app_logo")
        val overlayLabels = listOf("REST TIMER", "WORKOUT COMPLETE", "REPS IN RESERVE")
        val overlayCounts = overlayLabels.associateWith {
            compose.onAllNodesWithText(it).fetchSemanticsNodes().size
        }
        val diagnostics = "RENDER_METRICS $route title=$titleBright/$titleSamples " +
            "header=$headerBright/$headerSamples overlays=$overlayCounts"
        println(diagnostics)
        File(context.filesDir, "render-debug-$route.txt").writeText(diagnostics)
        // External app-specific folder can be pulled with adb even if run-as fails.
        context.getExternalFilesDir(null)?.let { directory ->
            out.copyTo(File(directory, out.name), overwrite = true)
            File(directory, "render-debug-$route.txt").writeText(diagnostics)
        }

        // The header is an independent rendering control. If both are black,
        // node capture itself is suspect; if only content is dark, investigate
        // the screen host or a scrim in its content area.
        return (titleBright > titleSamples / 100) to diagnostics
    }
}
