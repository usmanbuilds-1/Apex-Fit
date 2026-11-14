package com.apexfit.app.utils

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExerciseVideoAssetTest {

    @Test
    fun curatedVideoAsset_hasOnlyValidConservativeMappings() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val raw = context.assets.open("exercise_videos.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)

        assertEquals(248, root.length())

        root.keys().forEachRemaining { exerciseId ->
            val entry = root.getJSONObject(exerciseId)
            val videoId = entry.getString("video_id")
            val matchType = entry.getString("match_type")
            val score = entry.getInt("match_score")

            assertTrue("Invalid YouTube id for $exerciseId", Regex("^[A-Za-z0-9_-]{11}$").matches(videoId))
            assertTrue("Unexpected match type for $exerciseId", matchType == "exact" || matchType == "fuzzy")
            if (matchType == "fuzzy") {
                assertTrue("Fuzzy score below production threshold for $exerciseId", score >= 65)
            }
            assertEquals("DEMIC", entry.getString("channel"))
        }
    }
}
