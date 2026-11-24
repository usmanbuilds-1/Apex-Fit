package com.apexfit.app.ui.components

import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.ExerciseMedia
import com.apexfit.app.utils.ExerciseMediaCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ExerciseMediaPanel(
    exerciseId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var media by remember(exerciseId) { mutableStateOf<ExerciseMedia?>(null) }
    var isLoaded by remember(exerciseId) { mutableStateOf(false) }
    var loadFailed by remember(exerciseId) { mutableStateOf(false) }
    var videoUnavailable by remember(exerciseId) { mutableStateOf(false) }

    LaunchedEffect(exerciseId) {
        try {
            media = withContext(Dispatchers.IO) {
                ExerciseMediaCatalog.get(context, exerciseId)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            isLoaded = true
        }
    }
    ExerciseMediaPanelContent(
        media = media,
        isLoaded = isLoaded,
        loadFailed = loadFailed,
        videoUnavailable = videoUnavailable,
        modifier = modifier,
        onVideoUnavailable = { videoUnavailable = true },
        onRetryVideo = { videoUnavailable = false }
    )
}

@Composable
internal fun ExerciseMediaPanelContent(
    media: ExerciseMedia?,
    isLoaded: Boolean,
    loadFailed: Boolean,
    videoUnavailable: Boolean = false,
    modifier: Modifier = Modifier,
    onVideoUnavailable: () -> Unit = {},
    onRetryVideo: () -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Technique video", style = MaterialTheme.typography.titleMedium)
        when {
            !isLoaded -> Text("Checking for an available technique video.", color = ApexColors.TextSecondary)
            loadFailed -> Text(
                "Video information could not be loaded. Follow the written instructions above.",
                color = ApexColors.Alert
            )
            media?.youtubeVideoId == null ->
                Text("No mapped technique video is available. Follow the written instructions.", color = ApexColors.TextSecondary)
            videoUnavailable -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This video couldn’t load. Check your connection or follow the written instructions above.",
                    color = ApexColors.TextSecondary)
                TextButton(onClick = onRetryVideo, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Try again", color = ApexColors.Primary)
                }
            }
            else -> YouTubeTechniqueVideo(
                videoId = media.youtubeVideoId,
                title = media.youtubeVideoTitle,
                matchType = media.youtubeMatchType,
                channel = media.youtubeChannel,
                onUnavailable = onVideoUnavailable
            )
        }
    }
}

@Composable
private fun YouTubeTechniqueVideo(
    videoId: String,
    title: String?,
    matchType: String?,
    channel: String?,
    onUnavailable: () -> Unit
) {
    var showPlayer by rememberSaveable(videoId) { mutableStateOf(false) }
    var webView by remember(videoId) { mutableStateOf<WebView?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (matchType == "exact") "Video mapped to this exercise" else "Related movement video",
            style = MaterialTheme.typography.labelLarge,
            color = ApexColors.TextSecondary
        )

        if (matchType != "exact") {
            Text(
                "This is a related movement, not a verified demo for this exact exercise. Check that the exercise shown matches before following it.",
                style = MaterialTheme.typography.bodySmall,
                color = ApexColors.TextTertiary
            )
        }

        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = ApexColors.TextPrimary
            )
        }

        if (!channel.isNullOrBlank()) {
            Text(
                text = channel,
                style = MaterialTheme.typography.bodySmall,
                color = ApexColors.TextTertiary
            )
        }

        if (!showPlayer) {
            OutlinedButton(
                onClick = { showPlayer = true },
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexColors.BorderStrong),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(
                    text = "Play technique video",
                    style = MaterialTheme.typography.labelLarge,
                    color = ApexColors.TextPrimary
                )
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(8.dp))
                    .align(Alignment.CenterHorizontally),
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        setBackgroundColor(AndroidColor.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    showPlayer = false
                                    onUnavailable()
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                if (request?.isForMainFrame == true &&
                                    (errorResponse?.statusCode ?: 200) >= 400
                                ) {
                                    showPlayer = false
                                    onUnavailable()
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                if (request?.isForMainFrame != true) return false
                                val host = request.url.host?.lowercase().orEmpty()
                                return !isAllowedYouTubeHost(host)
                            }
                        }
                        loadUrl(
                            "https://www.youtube-nocookie.com/embed/$videoId" +
                                "?playsinline=1&rel=0&autoplay=0"
                        )
                    }
                }
            )
            Text(
                text = "Streamed by YouTube. Apex Fit does not download or rehost the video.",
                style = MaterialTheme.typography.bodySmall,
                color = ApexColors.TextTertiary
            )
        }
    }

    DisposableEffect(videoId, showPlayer) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webView = null
        }
    }
}

private fun isAllowedYouTubeHost(host: String): Boolean {
    return host == "youtube.com" ||
        host.endsWith(".youtube.com") ||
        host == "youtube-nocookie.com" ||
        host.endsWith(".youtube-nocookie.com") ||
        host == "googlevideo.com" ||
        host.endsWith(".googlevideo.com") ||
        host == "ytimg.com" ||
        host.endsWith(".ytimg.com") ||
        host == "google.com" ||
        host.endsWith(".google.com")
}
