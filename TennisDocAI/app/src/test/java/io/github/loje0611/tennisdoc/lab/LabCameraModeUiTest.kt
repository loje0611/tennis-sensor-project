package io.github.loje0611.tennisdoc.lab

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import io.github.loje0611.tennisdoc.feature.lab.ui.BodyFramingGuide
import io.github.loje0611.tennisdoc.feature.lab.ui.CameraFacingMode
import io.github.loje0611.tennisdoc.feature.lab.ui.FarFieldFeedbackOverlay
import io.github.loje0611.tennisdoc.feature.lab.ui.FarFieldHudState
import io.github.loje0611.tennisdoc.feature.lab.ui.LabSessionControlHeader
import io.github.loje0611.tennisdoc.feature.lab.ui.PoseOverlayCanvas
import io.github.loje0611.tennisdoc.feature.lab.ui.SessionCompletionDialog
import io.github.loje0611.tennisdoc.feature.lab.ui.SessionCompletionSummary
import io.github.loje0611.tennisdoc.feature.lab.ui.SetupCountdownOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LabCameraModeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun ac1_cameraFacingToggleRendersFrontAndBackAndInvokesCallback() {
        var mode by mutableStateOf(CameraFacingMode.FRONT)
        var toggleCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                LabSessionControlHeader(
                    selectedDrill = DrillType.FOREHAND,
                    isSessionActive = false,
                    sessionDurationSeconds = 0L,
                    swingCount = 0,
                    isSensorConnected = true,
                    onStartSession = {},
                    onFinishSession = {},
                    cameraFacingMode = mode,
                    onToggleCameraFacing = {
                        toggleCount++
                        mode = if (mode == CameraFacingMode.FRONT) {
                            CameraFacingMode.BACK
                        } else {
                            CameraFacingMode.FRONT
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("🔄 전면").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, toggleCount)
        composeTestRule.onNodeWithText("🔄 후면").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, toggleCount)
        composeTestRule.onNodeWithText("🔄 전면").assertIsDisplayed()
    }

    @Test
    fun setupCountdownOverlay_rendersNumbersAndCancelButton() {
        var cancelClicked = false

        composeTestRule.setContent {
            MaterialTheme {
                SetupCountdownOverlay(
                    countdownSeconds = 5,
                    onCancel = { cancelClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("준비하세요!").assertExists()
        composeTestRule.onNodeWithText("5").assertExists()
        composeTestRule.onNodeWithText("취소").assertExists()
        composeTestRule.onNodeWithText("취소").performClick()
        assertTrue(cancelClicked)
    }

    @Test
    fun farFieldFeedbackOverlay_rendersLargeHudWhenVisible() {
        val hudState = FarFieldHudState(
            faceText = "SQUARE 0°",
            faceColorHex = 0xFF00E676,
            energyEfficiency = 92f,
            isSquare = true
        )

        composeTestRule.setContent {
            MaterialTheme {
                FarFieldFeedbackOverlay(
                    hudState = hudState,
                    isFrontCamera = true
                )
            }
        }

        composeTestRule.onNodeWithText("SQUARE 0°").assertExists()
        composeTestRule.onNodeWithText("효율 92%").assertExists()
        composeTestRule.onNodeWithText("🟢 완벽한 정타 & 에너지 전달").assertExists()
    }

    @Test
    fun bodyFramingGuide_rendersReadyIndicatorWhenFramed() {
        composeTestRule.setContent {
            MaterialTheme {
                BodyFramingGuide(
                    isFrontCamera = true,
                    isSessionActive = false,
                    isBodyFramed = true
                )
            }
        }

        composeTestRule.onNodeWithText("🟢 READY (준비 완료)").assertExists()
    }

    @Test
    fun sessionCompletionDialog_rendersMetricsAndTriggersCallbacks() {
        val summary = SessionCompletionSummary(
            sessionId = "sess-lab-xyz",
            drillName = "포핸드",
            totalSwingCount = 12,
            durationSeconds = 185L,
            squareRatePercent = 83,
            averageEnergyEfficiency = 91.5f,
            latestRecordId = 101L
        )

        var navigatedSessionId = ""
        var navigatedRecordId = 0L
        var dismissed = false

        composeTestRule.setContent {
            MaterialTheme {
                SessionCompletionDialog(
                    summary = summary,
                    onDismiss = { dismissed = true },
                    onNavigateToReplay = { sId, rId ->
                        navigatedSessionId = sId
                        navigatedRecordId = rId
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("🎯 포핸드 훈련 완료!").assertExists()
        composeTestRule.onNodeWithText("12회").assertExists()
        composeTestRule.onNodeWithText("03:05").assertExists()
        composeTestRule.onNodeWithText("83%").assertExists()
        composeTestRule.onNodeWithText("91.5%").assertExists()

        composeTestRule.onNodeWithText("🎬 리플레이 보기").performClick()
        assertEquals("sess-lab-xyz", navigatedSessionId)
        assertEquals(101L, navigatedRecordId)
        assertTrue(dismissed)
    }

    @Test
    fun ac3_countdownShowsStartLabelAtZero() {
        composeTestRule.setContent {
            MaterialTheme {
                SetupCountdownOverlay(countdownSeconds = 0, onCancel = {})
            }
        }
        composeTestRule.onNodeWithText("시작!").assertIsDisplayed()
        composeTestRule.onNodeWithText("취소").assertIsDisplayed()
    }

    @Test
    fun ac2_poseOverlayCanvasAcceptsMirroredFrontPoseWithoutCrash() {
        val pose = PoseFrame(
            landmarks = List(33) { PoseLandmark(x = 0.2f, y = 0.5f, z = 0f, visibility = 0.9f) }
        )
        composeTestRule.setContent {
            MaterialTheme {
                PoseOverlayCanvas(poseFrame = pose, isMirrored = true)
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun ac5_farFieldHudHiddenOnBackCameraAndShowsDefectCopyWhenOpen() {
        val hudState = FarFieldHudState(
            faceText = "OPEN +12°",
            faceColorHex = 0xFFFF1744,
            energyEfficiency = 61f,
            isSquare = false
        )
        var isFront by mutableStateOf(false)
        composeTestRule.setContent {
            MaterialTheme {
                FarFieldFeedbackOverlay(hudState = hudState, isFrontCamera = isFront)
            }
        }
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("OPEN +12°").fetchSemanticsNodes().size
        )

        isFront = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("OPEN +12°").assertIsDisplayed()
        composeTestRule.onNodeWithText("효율 61%").assertIsDisplayed()
        composeTestRule.onNodeWithText("🔴 페이스 각도 보정이 필요합니다").assertIsDisplayed()
    }

    @Test
    fun bodyFramingGuide_hiddenOnBackCameraAndDuringSession() {
        var isFront by mutableStateOf(false)
        var sessionActive by mutableStateOf(false)
        composeTestRule.setContent {
            MaterialTheme {
                BodyFramingGuide(
                    isFrontCamera = isFront,
                    isSessionActive = sessionActive,
                    isBodyFramed = true
                )
            }
        }
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("🟢 READY (준비 완료)").fetchSemanticsNodes().size
        )

        isFront = true
        sessionActive = true
        composeTestRule.waitForIdle()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("🟢 READY (준비 완료)").fetchSemanticsNodes().size
        )
    }

    @Test
    fun ac7_dismissButtonClearsDialogWithoutReplayNavigation() {
        val summary = SessionCompletionSummary(
            sessionId = "sess-lab-xyz",
            drillName = "포핸드",
            totalSwingCount = 12,
            durationSeconds = 185L,
            squareRatePercent = 83,
            averageEnergyEfficiency = 91.5f
        )
        var navigatedSessionId = ""
        var navigatedRecordId = 0L
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                SessionCompletionDialog(
                    summary = summary,
                    onDismiss = { dismissed = true },
                    onNavigateToReplay = { sId, rId ->
                        navigatedSessionId = sId
                        navigatedRecordId = rId
                    }
                )
            }
        }
        composeTestRule.onNodeWithText("닫기 / 새 훈련").performClick()
        assertTrue(dismissed)
        assertEquals("", navigatedSessionId)
        assertEquals(0L, navigatedRecordId)
    }
}
