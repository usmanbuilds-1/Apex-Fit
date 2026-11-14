package com.apexfit.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExerciseMediaCatalogTest {

    @Test
    fun youtubeShortUrl_extractsVideoId() {
        assertEquals(
            "dQw4w9WgXcQ",
            ExerciseMediaCatalog.normalizeYouTubeVideoId(
                "https://www.youtube.com/shorts/dQw4w9WgXcQ?feature=share"
            )
        )
    }

    @Test
    fun youtubeWatchAndShortLinks_shareSameParser() {
        assertEquals(
            "dQw4w9WgXcQ",
            ExerciseMediaCatalog.normalizeYouTubeVideoId(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            )
        )
        assertEquals(
            "dQw4w9WgXcQ",
            ExerciseMediaCatalog.normalizeYouTubeVideoId(
                "https://youtu.be/dQw4w9WgXcQ"
            )
        )
    }

    @Test
    fun unsupportedHost_isRejected() {
        assertNull(
            ExerciseMediaCatalog.normalizeYouTubeVideoId(
                "https://example.com/shorts/dQw4w9WgXcQ"
            )
        )
    }

    @Test
    fun catalogCombinesImagesAndOptionalVideo() {
        val media = """
            {
              "dumbbell-lateral-raise": {
                "source_id": "Side_Lateral_Raise",
                "images": ["Side_Lateral_Raise/0.jpg", "Side_Lateral_Raise/1.jpg"],
                "instructions": ["Stand tall.", "Raise under control."]
              }
            }
        """.trimIndent()
        val videos = """
            {
              "dumbbell-lateral-raise": {
                "video_id": "dQw4w9WgXcQ",
                "title": "Perfect Lateral Raise",
                "match_type": "fuzzy",
                "match_score": 89,
                "channel": "DEMIC"
              }
            }
        """.trimIndent()

        val result = ExerciseMediaCatalog.parseCatalog(media, videos)
            .getValue("dumbbell-lateral-raise")

        assertEquals("Side_Lateral_Raise", result.sourceId)
        assertEquals("dQw4w9WgXcQ", result.youtubeVideoId)
        assertEquals("Perfect Lateral Raise", result.youtubeVideoTitle)
        assertEquals("fuzzy", result.youtubeMatchType)
        assertEquals(89, result.youtubeMatchScore)
        assertEquals("DEMIC", result.youtubeChannel)
        assertEquals(2, result.imageUrls.size)
        assertEquals(listOf("Stand tall.", "Raise under control."), result.instructions)
        assertTrue(result.imageUrls.first().contains("Side_Lateral_Raise/0.jpg"))
    }
}

    @Test
    fun legacyStringVideoMapping_remainsSupported() {
        val media = """
            {
              "plank": {
                "source_id": "Plank",
                "images": [],
                "instructions": []
              }
            }
        """.trimIndent()
        val videos = """{"plank":"https://youtu.be/dQw4w9WgXcQ"}"""

        val result = ExerciseMediaCatalog.parseCatalog(media, videos).getValue("plank")
        assertEquals("dQw4w9WgXcQ", result.youtubeVideoId)
        assertNull(result.youtubeVideoTitle)
    }
