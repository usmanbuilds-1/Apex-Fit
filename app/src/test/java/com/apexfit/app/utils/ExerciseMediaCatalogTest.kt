package com.apexfit.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
                "images": ["Side_Lateral_Raise/0.jpg", "Side_Lateral_Raise/1.jpg"]
              }
            }
        """.trimIndent()
        val videos = """
            {
              "dumbbell-lateral-raise": "https://youtube.com/shorts/dQw4w9WgXcQ"
            }
        """.trimIndent()

        val result = ExerciseMediaCatalog.parseCatalog(media, videos)
            .getValue("dumbbell-lateral-raise")

        assertEquals("Side_Lateral_Raise", result.sourceId)
        assertEquals("dQw4w9WgXcQ", result.youtubeVideoId)
        assertEquals(2, result.imageUrls.size)
        assertTrue(result.imageUrls.first().contains("Side_Lateral_Raise/0.jpg"))
    }
}
