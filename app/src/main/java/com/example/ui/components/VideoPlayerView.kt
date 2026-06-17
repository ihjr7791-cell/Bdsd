package com.example.ui.components

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    url: String,
    modifier: Modifier = Modifier,
    onPlayerError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }

    // Remember the ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Set up the MediaItem whenever the URL changes
    LaunchedEffect(url) {
        isBuffering = true
        try {
            val mediaItem = MediaItem.fromUri(url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        } catch (e: Exception) {
            Log.e("VideoPlayerView", "Error preparing player media", e)
            onPlayerError("حدث خطأ في تحميل الرابط: ${e.localizedMessage}")
        }
    }

    // Connect player listeners
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = playbackState == Player.STATE_READY
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("VideoPlayerView", "ExoPlayer playback error occured", error)
                val cleanError = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        "فشل الاتصال بالإنترنت أو انتهت مهلة سيرفر البث"
                    }
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                        "تنسيق الفيديو غير مدعوم على المشغل"
                    }
                    else -> "فشل تشغيل القناة الرياضية من المصدر"
                }
                onPlayerError(cleanError)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // UI Layout wrapper
    Box(
        modifier = modifier
            .background(Color.Black)
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true // Standard native controls
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00FF66)) // Neon green accent color
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "جاري تهيئة البث المباشر...",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
