package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
                title = { Text("동기 리플레이 & 정밀 진단") },
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
        if (uiState.fusedSwing == null) {
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
            val swing = uiState.fusedSwing!!
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 1. 포즈 스켈레톤 리플레이 캔버스
                PoseReplayCanvas(
                    poseFrame = uiState.currentPoseFrame,
                    isImpact = uiState.isImpactFrame,
                    tooltips = uiState.tooltips,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. 동기화 타임라인 컨트롤러
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

                // 3. IMU 동기 파형 차트
                ImuWaveformChart(
                    imuSamples = swing.imuSamples,
                    kineticChain = swing.kineticChain,
                    currentTimestampMs = uiState.currentTimestampMs,
                    timeOffsetMs = if (swing.anchor.isSynchronized) swing.anchor.timeOffsetMs else 0L,
                    durationMs = uiState.durationMs
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 5단계 운동 체인 & 인과 진단 요약 카드
                KineticChainSummaryCard(
                    kineticChain = swing.kineticChain,
                    racketImpact = swing.racketImpact,
                    diagnosis = swing.diagnosis
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
