package com.apexfit.app.ui.components

import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.ExerciseMedia
import com.apexfit.app.utils.ExerciseMediaCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ExerciseMediaPanel(
    exerciseId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var media by remember(exerciseId) { mutableStateOf<ExerciseMedia?>(null) }

    LaunchedEffect(exerciseId) {
        media = withContext(Dispatchers.IO) {
            ExerciseMediaCatalog.get(context, exerciseId)
        }
    }

    val currentMedia = media ?: return
    if (currentMedia.imageUrls.isEmpty() && currentMedia.youtubeVideoId == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (currentMedia.imageUrls.isNotEmpty()) {
            Text(
                text = "FORM REFERENCE",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AmberAccent
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(currentMedia.imageUrls, key = { index, url -> "$index:$url" }) { index, url ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AsyncImage(
                            model = url,
                            contentDescription = "Exercise form reference ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkRaised)
                        )
                        Text(
                            text = if (index == 0) "START" else "FINISH",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = MutedText
                        )
                    }
                }
            }
            Text(
                text = "Images: Free Exercise DB · public domain",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = MutedText
            )
        }

        currentMedia.youtubeVideoId?.let { videoId ->
            YouTubeTechniqueVideo(
                videoId = videoId,
                title = currentMedia.youtubeVideoTitle,
                matchType = currentMedia.youtubeMatchType,
                channel = currentMedia.youtubeChannel
            )
        }
    }
}

@Composable
private fun YouTubeTechniqueVideo(
    videoId: String,
    title: String?,
    matchType: String?,
    channel: String?
) {
    var showPlayer by rememberSaveable(videoId) { mutableStateOf(false) }
    var webView by remember(videoId) { mutableStateOf<WebView?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (matchType == "exact") "TECHNIQUE VIDEO" else "RELATED TECHNIQUE",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AmberAccent
        )

        title?.let {
            Text(
                text = it,
                fontFamily = InterFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText,
                lineHeight = 18.sp
            )
        }

        if (!channel.isNullOrBlank()) {
            Text(
                text = if (matchType == "exact") channel else "$channel · related movement guide",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = MutedText
            )
        }

        if (!showPlayer) {
            OutlinedButton(
                onClick = { showPlayer = true },
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = ApexColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Play technique video",
                    fontFamily = InterFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText
                )
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(14.dp))
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
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = MutedText
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
