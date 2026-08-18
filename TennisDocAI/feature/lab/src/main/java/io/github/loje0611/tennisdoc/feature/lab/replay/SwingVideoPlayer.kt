package io.github.loje0611.tennisdoc.feature.lab.replay

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun SwingVideoPlayer(
    videoPath: String,
    currentTimestampMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var playbackError by remember(videoPath) { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(videoPath) {
        playbackError = false
        val created = try {
            ExoPlayer.Builder(context.applicationContext).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = false
                prepare()
            }
        } catch (_: Exception) {
            playbackError = true
            null
        }
        player = created
        onDispose {
            created?.release()
            player = null
        }
    }

    val exoPlayer = player
    LaunchedEffect(exoPlayer, isPlaying) {
        exoPlayer?.playWhenReady = isPlaying
    }

    LaunchedEffect(exoPlayer, playbackSpeed) {
        exoPlayer?.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(exoPlayer, currentTimestampMs, isPlaying) {
        val current = exoPlayer ?: return@LaunchedEffect
        val drift = abs(current.currentPosition - currentTimestampMs)
        if (!isPlaying || drift > 100L) {
            current.seekTo(currentTimestampMs.coerceAtLeast(0L))
        }
    }

    when {
        playbackError -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "영상을 재생할 수 없습니다",
                    color = Color.White
                )
            }
        }
        exoPlayer != null -> {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = exoPlayer
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                },
                modifier = modifier.fillMaxSize()
            )
        }
        else -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }
}
