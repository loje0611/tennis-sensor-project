package io.github.loje0611.tennisdoc.lab

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.feature.lab.ui.BodyFramingGuide
import io.github.loje0611.tennisdoc.feature.lab.ui.CameraFacingMode
import io.github.loje0611.tennisdoc.feature.lab.ui.FarFieldFeedbackOverlay
import io.github.loje0611.tennisdoc.feature.lab.ui.FarFieldHudState
import io.github.loje0611.tennisdoc.feature.lab.ui.LabSessionControlHeader
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
@Config(manifest = Config.NONE)
class LabCameraModeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun cameraFacingToggle_togglesModeAndRendersCorrectPill() {
        var toggleClicked = false

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
                    cameraFacingMode = CameraFacingMode.FRONT,
                    onToggleCameraFacing = { toggleClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("🔄 전면").assertExists()
        composeTestRule.onNodeWithText("🔄 전면").performClick()
        assertTrue(toggleClicked)
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
            averageEnergyEfficiency = 91.5f
        )

        var navigatedSessionId = ""
        var dismissed = false

        composeTestRule.setContent {
            MaterialTheme {
                SessionCompletionDialog(
                    summary = summary,
                    onDismiss = { dismissed = true },
                    onNavigateToReplay = { navigatedSessionId = it }
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
        assertTrue(dismissed)
    }
}
