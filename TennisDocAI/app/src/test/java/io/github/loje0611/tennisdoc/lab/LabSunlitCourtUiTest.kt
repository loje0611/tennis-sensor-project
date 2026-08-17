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
import androidx.compose.ui.test.performScrollTo
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.FusionDiagnosis
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.feature.lab.ui.CameraFacingMode
import io.github.loje0611.tennisdoc.feature.lab.ui.DrillSelectorBar
import io.github.loje0611.tennisdoc.feature.lab.ui.FarFieldFeedbackOverlay
import io.github.loje0611.tennisdoc.feature.lab.ui.FarFieldHudState
import io.github.loje0611.tennisdoc.feature.lab.ui.LabRealtimeFeedbackCard
import io.github.loje0611.tennisdoc.feature.lab.ui.LabSessionControlHeader
import io.github.loje0611.tennisdoc.feature.lab.ui.SessionCompletionDialog
import io.github.loje0611.tennisdoc.feature.lab.ui.SessionCompletionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LabSunlitCourtUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun ac1_sessionControlHeader_inactiveStateRendersFrostGlassStartAndGoal() {
        var startClicked = false

        composeTestRule.setContent {
            MaterialTheme {
                LabSessionControlHeader(
                    selectedDrill = DrillType.FOREHAND,
                    isSessionActive = false,
                    sessionDurationSeconds = 0L,
                    swingCount = 0,
                    isSensorConnected = true,
                    onStartSession = { startClicked = true },
                    onFinishSession = {},
                    cameraFacingMode = CameraFacingMode.FRONT,
                )
            }
        }

        composeTestRule.onNodeWithText("센서 연결됨").assertIsDisplayed()
        composeTestRule.onNodeWithText("목표: 포핸드").assertIsDisplayed()
        composeTestRule.onNodeWithText("🔄 전면").assertIsDisplayed()
        composeTestRule.onNodeWithText("측정 시작").assertIsDisplayed().performClick()
        assertTrue(startClicked)
    }

    @Test
    fun ac1_sessionControlHeader_activeStateRendersEndButtonAndMichromaMetrics() {
        var finishClicked = false

        composeTestRule.setContent {
            MaterialTheme {
                LabSessionControlHeader(
                    selectedDrill = DrillType.FOREHAND,
                    isSessionActive = true,
                    sessionDurationSeconds = 135L,
                    swingCount = 12,
                    isSensorConnected = true,
                    onStartSession = {},
                    onFinishSession = { finishClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("02:15 | 스윙 12회").assertIsDisplayed()
        composeTestRule.onNodeWithText("측정 종료").assertIsDisplayed().performClick()
        assertTrue(finishClicked)
    }

    @Test
    fun ac1_sessionControlHeader_disconnectedSensorShowsUnconnectedLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                LabSessionControlHeader(
                    selectedDrill = DrillType.SERVE,
                    isSessionActive = false,
                    sessionDurationSeconds = 0L,
                    swingCount = 0,
                    isSensorConnected = false,
                    onStartSession = {},
                    onFinishSession = {},
                )
            }
        }

        composeTestRule.onNodeWithText("센서 미연결").assertIsDisplayed()
        composeTestRule.onNodeWithText("목표: 서브").assertIsDisplayed()
    }

    @Test
    fun ac2_drillSelectorBar_rendersAllSnowWhiteCapsuleChipsAndAllowsSelection() {
        var selected by mutableStateOf(DrillType.FOREHAND)

        composeTestRule.setContent {
            MaterialTheme {
                DrillSelectorBar(
                    selectedDrill = selected,
                    isSessionActive = false,
                    onSelectDrill = { selected = it },
                )
            }
        }

        listOf("포핸드", "백핸드", "서브", "포발리", "백발리").forEach { label ->
            composeTestRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }

        composeTestRule.onNodeWithText("백핸드").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(DrillType.BACKHAND, selected)

        composeTestRule.onNodeWithText("포발리").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(DrillType.FOREHAND_VOLLEY, selected)
    }

    @Test
    fun ac4_realtimeFeedbackCard_rendersSquareBadgeChainNodesAndYellowTipBox() {
        composeTestRule.setContent {
            MaterialTheme {
                LabRealtimeFeedbackCard(
                    fusedSwing = fusedSwing(
                        faceState = RacketFaceState.SQUARE,
                        deviationDeg = 0f,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("임팩트 페이스").assertIsDisplayed()
        composeTestRule.onNodeWithText("스퀘어 (0°)").assertIsDisplayed()
        composeTestRule.onNodeWithText("5단계 운동 체인").assertIsDisplayed()
        composeTestRule.onNodeWithText("전달 효율 95%").assertIsDisplayed()
        listOf("골반", "어깨", "손목", "라켓", "임팩트").forEach { node ->
            composeTestRule.onNodeWithText(node).assertIsDisplayed()
        }
        composeTestRule.onNodeWithText("💡 운동 체인이 순차적으로 올바르게 전달되었습니다.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("🎯 훌륭한 임팩트입니다.").assertIsDisplayed()
    }

    @Test
    fun ac4_realtimeFeedbackCard_rendersOpenAndClosedHighContrastBadges() {
        var face by mutableStateOf(RacketFaceState.OPEN)

        composeTestRule.setContent {
            MaterialTheme {
                LabRealtimeFeedbackCard(
                    fusedSwing = fusedSwing(
                        faceState = face,
                        deviationDeg = if (face == RacketFaceState.OPEN) 12f else -8f,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("열림 (+12°)").assertIsDisplayed()

        face = RacketFaceState.CLOSED
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("닫힘 (-8°)").assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("열림 (+12°)").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun ac5_farFieldOverlay_rendersHighContrastWhiteHudOnFrontCamera() {
        composeTestRule.setContent {
            MaterialTheme {
                FarFieldFeedbackOverlay(
                    hudState = FarFieldHudState(
                        faceText = "SQUARE 0°",
                        faceColorHex = 0xFF10B981,
                        energyEfficiency = 92f,
                        isSquare = true,
                    ),
                    isFrontCamera = true,
                )
            }
        }

        composeTestRule.onNodeWithText("SQUARE 0°").assertIsDisplayed()
        composeTestRule.onNodeWithText("효율 92%").assertIsDisplayed()
        composeTestRule.onNodeWithText("🟢 완벽한 정타 & 에너지 전달").assertIsDisplayed()
    }

    @Test
    fun ac5_sessionCompletionDialog_rendersLightReportAndRoyalBlueReplay() {
        var replaySessionId = ""
        var replayRecordId = 0L

        composeTestRule.setContent {
            MaterialTheme {
                SessionCompletionDialog(
                    summary = SessionCompletionSummary(
                        sessionId = "sess-sunlit",
                        drillName = "포핸드",
                        totalSwingCount = 12,
                        durationSeconds = 135L,
                        squareRatePercent = 80,
                        averageEnergyEfficiency = 91.0f,
                        latestRecordId = 7L,
                    ),
                    onDismiss = {},
                    onNavigateToReplay = { sessionId, recordId ->
                        replaySessionId = sessionId
                        replayRecordId = recordId
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("🎯 포핸드 훈련 완료!").assertIsDisplayed()
        composeTestRule.onNodeWithText("총 스윙 수").assertIsDisplayed()
        composeTestRule.onNodeWithText("12회").assertIsDisplayed()
        composeTestRule.onNodeWithText("02:15").assertIsDisplayed()
        composeTestRule.onNodeWithText("🎬 리플레이 보기").assertIsDisplayed().performClick()
        assertEquals("sess-sunlit", replaySessionId)
        assertEquals(7L, replayRecordId)
    }

    private fun fusedSwing(
        faceState: RacketFaceState,
        deviationDeg: Float,
    ): FusedSwing {
        val stages = listOf(
            KineticStage(KineticStageType.HIP, 1000L, 10f),
            KineticStage(KineticStageType.SHOULDER, 1030L, 15f, 30L),
            KineticStage(KineticStageType.WRIST, 1060L, 20f, 30L),
            KineticStage(KineticStageType.RACKET, 1090L, 1500f, 30L),
            KineticStage(KineticStageType.IMPACT, 1110L, 25f, 20L),
        )
        return FusedSwing(
            swingId = "test-swing-1",
            sessionId = "sess-1",
            drillType = DrillType.FOREHAND,
            anchor = SyncAnchor(1000L, 1000L, 0L, 0.9f, true),
            kineticChain = KineticChain5Stage(stages, true, 110L, 94.5f),
            racketImpact = RacketImpactOrientation(0f, 0f, 0f, faceState, deviationDeg),
            visionPoses = emptyList(),
            imuSamples = emptyList(),
            diagnosis = FusionDiagnosis(
                diagnosisTags = listOf("CLEAN_STRIKE"),
                primaryCause = "정상 스윙",
                coachingFeedback = "훌륭한 임팩트입니다.",
                causalExplanation = "운동 체인이 순차적으로 올바르게 전달되었습니다.",
            ),
        )
    }
}
