package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabReplayScreen(
    viewModel: LabReplayViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("스윙 비디오 리플레이") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("⟵", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (!uiState.hasVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "리플레이 데이터가 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val videoPath = uiState.videoPath.orEmpty()
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    val overlaySize = Size(
                        constraints.maxWidth.toFloat(),
                        constraints.maxHeight.toFloat()
                    )
                    SwingVideoPlayer(
                        videoPath = videoPath,
                        currentTimestampMs = uiState.currentTimestampMs,
                        isPlaying = uiState.isPlaying,
                        playbackSpeed = uiState.playbackSpeed,
                        modifier = Modifier.fillMaxSize()
                    )
                    SwingTrailOverlay(
                        swingTrailPoints = uiState.swingTrailPoints,
                        isImpact = uiState.isImpactFrame,
                        canvasSize = overlaySize,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SynchronizedTimelineController(
                    currentTimestampMs = uiState.currentTimestampMs,
                    durationMs = uiState.durationMs,
                    isPlaying = uiState.isPlaying,
                    playbackSpeed = uiState.playbackSpeed,
                    onSeek = { viewModel.seekTo(it) },
                    onTogglePlay = { viewModel.togglePlay() },
                    onSpeedToggle = {
                        val nextSpeed = if (uiState.playbackSpeed <= 0.5f) 1.0f else 0.5f
                        viewModel.setPlaybackSpeed(nextSpeed)
                    },
                    onStepBack = { viewModel.stepBackward() },
                    onStepForward = { viewModel.stepForward() },
                    onJumpToImpact = { viewModel.jumpToImpact() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SwingAnalysisSummaryCard(
                    swingPathType = uiState.swingPathType,
                    faceStateLabel = uiState.faceStateLabel,
                    coachingOneLiner = uiState.coachingOneLiner
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
