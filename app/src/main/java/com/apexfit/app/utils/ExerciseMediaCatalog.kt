package com.apexfit.app.utils

import android.content.Context
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ExerciseMedia(
    val sourceId: String?,
    val imageUrls: List<String>,
    val youtubeVideoId: String?
)

object ExerciseMediaCatalog {
    private const val MEDIA_ASSET = "exercise_media.json"
    private const val VIDEO_ASSET = "exercise_videos.json"
    internal const val IMAGE_BASE_URL =
        "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

    @Volatile
    private var cachedCatalog: Map<String, ExerciseMedia>? = null

    fun get(context: Context, exerciseId: String): ExerciseMedia? {
        val catalog = cachedCatalog ?: synchronized(this) {
            cachedCatalog ?: loadCatalog(context.applicationContext).also { cachedCatalog = it }
        }
        return catalog[exerciseId]
    }

    private fun loadCatalog(context: Context): Map<String, ExerciseMedia> {
        val mediaJson = context.assets.open(MEDIA_ASSET).bufferedReader().use { it.readText() }
        val videosJson = context.assets.open(VIDEO_ASSET).bufferedReader().use { it.readText() }
        return parseCatalog(mediaJson, videosJson)
    }

    internal fun parseCatalog(mediaJson: String, videosJson: String): Map<String, ExerciseMedia> {
        val mediaRoot = JSONObject(mediaJson)
        val videoRoot = JSONObject(videosJson.ifBlank { "{}" })
        val exerciseIds = linkedSetOf<String>()

        mediaRoot.keys().forEachRemaining { exerciseIds.add(it) }
        videoRoot.keys().forEachRemaining { exerciseIds.add(it) }

        return exerciseIds.associateWith { exerciseId ->
            val mediaEntry = mediaRoot.optJSONObject(exerciseId)
            val sourceId = mediaEntry?.optString("source_id")?.takeIf { it.isNotBlank() }
            val images = mediaEntry?.optJSONArray("images")
            val imageUrls = buildList {
                if (images != null) {
                    for (index in 0 until images.length()) {
                        val path = images.optString(index).takeIf { it.isNotBlank() } ?: continue
                        add(buildImageUrl(path))
                    }
                }
            }

            val rawVideo = if (videoRoot.has(exerciseId) && !videoRoot.isNull(exerciseId)) {
                videoRoot.optString(exerciseId)
            } else {
                null
            }

            ExerciseMedia(
                sourceId = sourceId,
                imageUrls = imageUrls,
                youtubeVideoId = normalizeYouTubeVideoId(rawVideo)
            )
        }
    }

    internal fun buildImageUrl(path: String): String {
        val encodedPath = path
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
            }
        return IMAGE_BASE_URL + encodedPath
    }

    internal fun normalizeYouTubeVideoId(rawValue: String?): String? {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (YOUTUBE_ID.matches(value)) return value

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val host = uri.host
            ?.lowercase()
            ?.removePrefix("www.")
            ?.removePrefix("m.")
            ?: return null

        val candidate = when {
            host == "youtu.be" -> uri.path.orEmpty().trim('/').substringBefore('/')
            host == "youtube.com" || host.endsWith(".youtube.com") ||
                host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com") -> {
                val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
                when {
                    segments.firstOrNull() in setOf("shorts", "embed") -> segments.getOrNull(1)
                    uri.path == "/watch" -> queryParam(uri.rawQuery, "v")
                    else -> null
                }
            }
            else -> null
        }

        return candidate?.takeIf { YOUTUBE_ID.matches(it) }
    }

    private fun queryParam(rawQuery: String?, key: String): String? {
        return rawQuery
            ?.split('&')
            ?.asSequence()
            ?.mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size != 2) null
                else URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name()) to
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
            }
            ?.firstOrNull { it.first == key }
            ?.second
    }

    private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
}
