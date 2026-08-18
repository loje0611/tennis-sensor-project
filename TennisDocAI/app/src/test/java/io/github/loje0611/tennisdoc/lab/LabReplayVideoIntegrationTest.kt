package io.github.loje0611.tennisdoc.lab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.github.loje0611.tennisdoc.core.fusion.model.FusionDiagnosis
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import io.github.loje0611.tennisdoc.feature.lab.replay.LabReplayScreen
import io.github.loje0611.tennisdoc.feature.lab.replay.LabReplayViewModel
import io.github.loje0611.tennisdoc.feature.lab.replay.SwingAnalysisSummaryCard
import io.github.loje0611.tennisdoc.feature.lab.replay.racketFaceStateLabel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LabReplayVideoIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun media3ExoplayerAndPlayerViewResolveOnClasspath() {
        val exo = Class.forName("androidx.media3.exoplayer.ExoPlayer")
        val playerView = Class.forName("androidx.media3.ui.PlayerView")
        assertEquals("androidx.media3.exoplayer.ExoPlayer", exo.name)
        assertEquals("androidx.media3.ui.PlayerView", playerView.name)
    }

    @Test
    fun hasVideoFalse_showsEmptyReplayMessageAndHidesAnalysisChrome() {
        val viewModel = LabReplayViewModel()
        assertFalse(viewModel.uiState.value.hasVideo)

        composeRule.setContent {
            MaterialTheme {
                LabReplayScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }

        composeRule.onNodeWithText("스윙 비디오 리플레이").assertIsDisplayed()
        composeRule.onNodeWithText("리플레이 데이터가 없습니다").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("🎾 스윙 궤적 분석").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("🎯 임팩트 점프").fetchSemanticsNodes().size)
        assertEquals(
            0,
            composeRule.onAllNodesWithText("IMU 동기 파형 & 운동체인 피크").fetchSemanticsNodes().size,
        )
        assertEquals(0, composeRule.onAllNodesWithText("포즈 데이터 대기 중...").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("5단계 운동 체인 & 인과 진단").fetchSemanticsNodes().size)
    }

    @Test
    fun hasVideoTrue_rendersVideoTrailAnalysisAndOmitsImuSkeletonCards() {
        val video = File.createTempFile("lab-replay-clip", ".mp4")
        try {
            video.writeBytes(byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d))
            val viewModel = LabReplayViewModel()
            viewModel.setFusedSwing(sampleSwing(RacketFaceState.SQUARE), videoPath = video.absolutePath)
            viewModel.jumpToImpact()
            composeRule.waitForIdle()

            assertTrue(viewModel.uiState.value.hasVideo)
            assertEquals(video.absolutePath, viewModel.uiState.value.videoPath)
            assertTrue(viewModel.uiState.value.swingTrailPoints.isNotEmpty())
            assertEquals("🟢 정타 (스퀘어)", viewModel.uiState.value.faceStateLabel)

            composeRule.setContent {
                MaterialTheme {
                    LabReplayScreen(viewModel = viewModel, onNavigateBack = {})
                }
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithText("스윙 비디오 리플레이").assertIsDisplayed()
            composeRule.onNodeWithText("🎾 스윙 궤적 분석").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("🟢 정타 (스퀘어)").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("🎯 임팩트 점프").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("훌륭한 타이밍입니다. 동일한 템포를 유지하세요.")
                .performScrollTo()
                .assertIsDisplayed()
            assertEquals(
                0,
                composeRule.onAllNodesWithText("리플레이 데이터가 없습니다").fetchSemanticsNodes().size,
            )
            assertEquals(
                0,
                composeRule.onAllNodesWithText("IMU 동기 파형 & 운동체인 피크").fetchSemanticsNodes().size,
            )
            assertEquals(0, composeRule.onAllNodesWithText("포즈 데이터 대기 중...").fetchSemanticsNodes().size)
            assertEquals(
                0,
                composeRule.onAllNodesWithText("5단계 운동 체인 & 인과 진단").fetchSemanticsNodes().size,
            )
        } finally {
            video.delete()
        }
    }

    @Test
    fun analysisCardRendersKoreanFaceLabelsForSquareOpenAndClosed() {
        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SwingAnalysisSummaryCard(
                        swingPathType = "📈 탑스핀 (상향 스윙 궤적)",
                        faceStateLabel = racketFaceStateLabel(RacketFaceState.SQUARE),
                        coachingOneLiner = "스퀘어 코칭",
                    )
                    SwingAnalysisSummaryCard(
                        swingPathType = "⚡ 플랫 (수평 스윙 궤적)",
                        faceStateLabel = racketFaceStateLabel(RacketFaceState.OPEN),
                        coachingOneLiner = "열림 코칭",
                    )
                    SwingAnalysisSummaryCard(
                        swingPathType = "📉 슬라이스 (하향 스윙 궤적)",
                        faceStateLabel = racketFaceStateLabel(RacketFaceState.CLOSED),
                        coachingOneLiner = "닫힘 코칭",
                    )
                }
            }
        }

        composeRule.onNodeWithText("🟢 정타 (스퀘어)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("🟠 페이스 열림 (공이 뜨는 원인)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("🔵 페이스 닫힘 (네트에 걸리는 원인)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("📈 탑스핀 (상향 스윙 궤적)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun missingVideoPath_fallsBackToEmptyStateEvenWhenSwingIsLoaded() {
        val viewModel = LabReplayViewModel()
        viewModel.setFusedSwing(sampleSwing(RacketFaceState.OPEN), videoPath = "/tmp/does-not-exist-${System.nanoTime()}.mp4")
        assertFalse(viewModel.uiState.value.hasVideo)
        assertEquals("🟠 페이스 열림 (공이 뜨는 원인)", viewModel.uiState.value.faceStateLabel)

        composeRule.setContent {
            MaterialTheme {
                LabReplayScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }

        composeRule.onNodeWithText("리플레이 데이터가 없습니다").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("🎾 스윙 궤적 분석").fetchSemanticsNodes().size)
    }

    private fun sampleSwing(faceState: RacketFaceState): FusedSwing {
        val sampleLandmarks = List(33) { index ->
            val t = index / 32f
            PoseLandmark(x = 0.4f + t * 0.2f, y = 0.6f - t * 0.3f, z = 0f, visibility = 0.9f)
        }
        val poses = List(30) { frame ->
            val landmarks = sampleLandmarks.mapIndexed { i, lm ->
                if (i == 16) {
                    PoseLandmark(
                        x = 0.3f + frame * 0.01f,
                        y = 0.7f - frame * 0.015f,
                        z = 0f,
                        visibility = 0.9f,
                    )
                } else {
                    lm
                }
            }
            PoseFrame(landmarks = landmarks)
        }
        val imuPoints = List(50) { idx ->
            ImuDataPoint(
                timestampMs = 1000L + idx * 20L,
                accelX = 1.0f,
                accelY = 2.0f,
                accelZ = 0.5f,
                gyroX = 10f,
                gyroY = 20f,
                gyroZ = 5f,
            )
        }
        return FusedSwing(
            swingId = "swing_video",
            sessionId = "test_lab_session",
            drillType = DrillType.FOREHAND,
            anchor = SyncAnchor(
                visionImpactTimestampMs = 495L,
                sensorImpactTimestampMs = 1500L,
                timeOffsetMs = 1005L,
                confidence = 0.95f,
                isSynchronized = true,
            ),
            kineticChain = KineticChain5Stage(
                stages = listOf(
                    KineticStage(KineticStageType.HIP, 1400L, 100f),
                    KineticStage(KineticStageType.SHOULDER, 1440L, 120f),
                    KineticStage(KineticStageType.WRIST, 1480L, 150f),
                    KineticStage(KineticStageType.RACKET, 1500L, 200f),
                    KineticStage(KineticStageType.IMPACT, 1500L, 250f),
                ),
                isSequential = true,
                totalDurationMs = 100L,
                energyTransferEfficiency = 0.925f,
            ),
            racketImpact = RacketImpactOrientation(
                rollDeg = 0f,
                pitchDeg = 1.5f,
                yawDeg = 0f,
                faceState = faceState,
                deviationDeg = 2.0f,
            ),
            diagnosis = FusionDiagnosis(
                diagnosisTags = listOf("CLEAN_STRIKE", "SQUARE_FACE"),
                primaryCause = "OPTIMAL_TIMING",
                coachingFeedback = "훌륭한 타이밍입니다. 동일한 템포를 유지하세요.",
                causalExplanation = "골반-어깨-손목-라켓 순차 가속이 정확하며 최적의 임팩트를 형성했습니다.",
            ),
            visionPoses = poses,
            imuSamples = imuPoints,
        )
    }
}
