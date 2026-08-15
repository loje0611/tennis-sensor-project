package io.github.loje0611.tennisdoc.lab

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import io.github.loje0611.tennisdoc.feature.lab.ui.LabRealtimeFeedbackCard
import io.github.loje0611.tennisdoc.feature.lab.ui.LabSessionControlHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LabSunlitCourtUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun ac1_sessionControlHeader_inactiveStateRendersStartButton() {
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
                    onFinishSession = {}
                )
            }
        }

        composeTestRule.onNodeWithText("측정 시작").assertExists()
        composeTestRule.onNodeWithText("측정 시작").performClick()
        assertTrue(startClicked)
    }

    @Test
    fun ac1_sessionControlHeader_activeStateRendersEndButtonAndMetrics() {
        var finishClicked = false

        composeTestRule.setContent {
            MaterialTheme {
                LabSessionControlHeader(
                    selectedDrill = DrillType.FOREHAND,
                    isSessionActive = true,
                    sessionDurationSeconds = 75L,
                    swingCount = 5,
                    isSensorConnected = true,
                    onStartSession = {},
                    onFinishSession = { finishClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("01:15 | 스윙 5회").assertExists()
        composeTestRule.onNodeWithText("측정 종료").assertExists()
        composeTestRule.onNodeWithText("측정 종료").performClick()
        assertTrue(finishClicked)
    }

    @Test
    fun ac2_drillSelectorBar_rendersCapsuleChipsAndAllowsSelection() {
        var selected = DrillType.FOREHAND

        composeTestRule.setContent {
            MaterialTheme {
                DrillSelectorBar(
                    selectedDrill = selected,
                    isSessionActive = false,
                    onSelectDrill = { selected = it }
                )
            }
        }

        composeTestRule.onNodeWithText("포핸드").assertExists()
        composeTestRule.onNodeWithText("서브").assertExists()
        composeTestRule.onNodeWithText("서브").performClick()
        assertEquals(DrillType.SERVE, selected)
    }

    @Test
    fun ac4_realtimeFeedbackCard_rendersPureWhiteSportsHud() {
        val stages = listOf(
            KineticStage(KineticStageType.HIP, 1000L, 10f),
            KineticStage(KineticStageType.SHOULDER, 1030L, 15f, 30L),
            KineticStage(KineticStageType.WRIST, 1060L, 20f, 30L),
            KineticStage(KineticStageType.RACKET, 1090L, 1500f, 30L),
            KineticStage(KineticStageType.IMPACT, 1110L, 25f, 20L)
        )
        val chain = KineticChain5Stage(stages, true, 110L, 94.5f)
        val diagnosis = FusionDiagnosis(
            diagnosisTags = listOf("CLEAN_STRIKE"),
            primaryCause = "정상 스윙",
            coachingFeedback = "훌륭한 임팩트입니다.",
            causalExplanation = "운동 체인이 순차적으로 올바르게 전달되었습니다."
        )
        val fused = FusedSwing(
            swingId = "test-swing-1",
            sessionId = "sess-1",
            drillType = DrillType.FOREHAND,
            anchor = SyncAnchor(1000L, 1000L, 0L, 0.9f, true),
            kineticChain = chain,
            racketImpact = RacketImpactOrientation(0f, 0f, 0f, RacketFaceState.SQUARE, 0f),
            visionPoses = emptyList(),
            imuSamples = emptyList(),
            diagnosis = diagnosis
        )

        composeTestRule.setContent {
            MaterialTheme {
                LabRealtimeFeedbackCard(fusedSwing = fused)
            }
        }

        composeTestRule.onNodeWithText("임팩트 페이스").assertExists()
        composeTestRule.onNodeWithText("스퀘어 (0°)").assertExists()
        composeTestRule.onNodeWithText("5단계 운동 체인").assertExists()
        composeTestRule.onNodeWithText("전달 효율 95%").assertExists()
        composeTestRule.onNodeWithText("💡 운동 체인이 순차적으로 올바르게 전달되었습니다.").assertExists()
        composeTestRule.onNodeWithText("🎯 훌륭한 임팩트입니다.").assertExists()
    }
}
