package io.github.loje0611.tennisdoc.lab

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.loje0611.tennisdoc.core.fusion.model.FusionDiagnosis
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import io.github.loje0611.tennisdoc.feature.lab.replay.KineticChainSummaryCard
import io.github.loje0611.tennisdoc.feature.lab.replay.PoseReplayCanvas
import io.github.loje0611.tennisdoc.feature.lab.replay.SynchronizedTimelineController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LabReplayUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ac3AndAc4_timelineJumpPlaySpeedAndFrameStep() {
        var currentTs by mutableLongStateOf(0L)
        var playing by mutableStateOf(false)
        var speed by mutableFloatStateOf(1.0f)
        var jumpClicked = false

        composeRule.setContent {
            MaterialTheme {
                SynchronizedTimelineController(
                    currentTimestampMs = currentTs,
                    durationMs = 957L,
                    isPlaying = playing,
                    playbackSpeed = speed,
                    onSeek = { currentTs = it },
                    onTogglePlay = { playing = !playing },
                    onSpeedToggle = { speed = if (speed <= 0.5f) 1.0f else 0.5f },
                    onStepBack = { currentTs = (currentTs - 33L).coerceAtLeast(0L) },
                    onStepForward = { currentTs = (currentTs + 33L).coerceAtMost(957L) },
                    onJumpToImpact = {
                        jumpClicked = true
                        currentTs = 495L
                    }
                )
            }
        }

        composeRule.onNodeWithText("🎯 임팩트 점프").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(jumpClicked)
        assertEquals(495L, currentTs)

        composeRule.onNodeWithText("1.0x").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("0.5x").assertIsDisplayed()

        composeRule.onNodeWithText("재생").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("정지").assertIsDisplayed()

        composeRule.onNodeWithText("▶").performClick()
        composeRule.waitForIdle()
        assertEquals(528L, currentTs)

        composeRule.onNodeWithText("◀").performClick()
        composeRule.waitForIdle()
        assertEquals(495L, currentTs)
    }

    @Test
    fun ac3_impactBadgeIsShownOnImpactFrame() {
        val landmarks = List(33) { PoseLandmark(x = 0.5f, y = 0.5f, z = 0f, visibility = 0.9f) }

        composeRule.setContent {
            MaterialTheme {
                PoseReplayCanvas(
                    poseFrame = PoseFrame(landmarks = landmarks),
                    isImpact = true,
                    tooltips = emptyList()
                )
            }
        }

        composeRule.onNodeWithText("⚡ IMPACT!").assertIsDisplayed()
    }

    @Test
    fun ac6_kineticChainCardRendersStagesEfficiencyAndCoaching() {
        composeRule.setContent {
            MaterialTheme {
                KineticChainSummaryCard(
                    kineticChain = KineticChain5Stage(
                        stages = listOf(
                            KineticStage(KineticStageType.HIP, 1400L, 100f),
                            KineticStage(KineticStageType.SHOULDER, 1440L, 120f),
                            KineticStage(KineticStageType.WRIST, 1480L, 150f),
                            KineticStage(KineticStageType.RACKET, 1500L, 200f),
                            KineticStage(KineticStageType.IMPACT, 1500L, 250f)
                        ),
                        isSequential = true,
                        totalDurationMs = 100L,
                        energyTransferEfficiency = 0.925f
                    ),
                    racketImpact = RacketImpactOrientation(
                        rollDeg = 0f,
                        pitchDeg = 1.5f,
                        yawDeg = 0f,
                        faceState = RacketFaceState.SQUARE,
                        deviationDeg = 2.0f
                    ),
                    diagnosis = FusionDiagnosis(
                        diagnosisTags = listOf("CLEAN_STRIKE"),
                        primaryCause = "OPTIMAL_TIMING",
                        coachingFeedback = "훌륭한 타이밍입니다. 동일한 템포를 유지하세요.",
                        causalExplanation = "골반-어깨-손목-라켓 순차 가속이 정확하며 최적의 임팩트를 형성했습니다."
                    )
                )
            }
        }

        composeRule.onNodeWithText("골반").assertIsDisplayed()
        composeRule.onNodeWithText("어깨").assertIsDisplayed()
        composeRule.onNodeWithText("손목").assertIsDisplayed()
        composeRule.onNodeWithText("라켓").assertIsDisplayed()
        composeRule.onNodeWithText("임팩트").assertIsDisplayed()
        composeRule.onNodeWithText("Face: SQUARE").assertIsDisplayed()
        composeRule.onNodeWithText("에너지 전달 효율: 92%").assertIsDisplayed()
        composeRule.onNodeWithText("✓ 운동 체인 순차 가속 정상").assertIsDisplayed()
        composeRule.onNodeWithText(
            "💡 골반-어깨-손목-라켓 순차 가속이 정확하며 최적의 임팩트를 형성했습니다."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("👉 훌륭한 타이밍입니다. 동일한 템포를 유지하세요.").assertIsDisplayed()
    }
}
