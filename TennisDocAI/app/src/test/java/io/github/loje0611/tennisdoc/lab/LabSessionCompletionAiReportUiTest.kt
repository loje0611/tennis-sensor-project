package io.github.loje0611.tennisdoc.lab

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CausalFlawDiagnosis
import io.github.loje0611.tennisdoc.core.model.DrillRecommendation
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.feature.lab.ui.SessionCompletionDialog
import io.github.loje0611.tennisdoc.feature.lab.ui.SessionCompletionSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LabSessionCompletionAiReportUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val summary = SessionCompletionSummary(
        sessionId = "sess-lab-ai",
        drillName = "포핸드",
        totalSwingCount = 12,
        durationSeconds = 185L,
        squareRatePercent = 83,
        averageEnergyEfficiency = 91.5f
    )

    private fun dummyReport(isFallback: Boolean) = AiCoachReport(
        reportId = "r-lab-ai",
        sessionId = "sess-lab-ai",
        generatedAtMillis = 1_000L,
        overallSummary = "세션 총평 텍스트",
        keyStrengths = listOf("우수한 임팩트 정타율"),
        primaryFlawDiagnosis = CausalFlawDiagnosis(
            flawTitle = "FACE_OPEN",
            observedEffect = "임팩트 시 라켓 페이스 열림",
            rootCause = "손목 젖혀짐",
            coachingCue = "라켓면을 스퀘어로",
        ),
        actionItems = listOf("타점 맞추기"),
        recommendedDrills = listOf(
            DrillRecommendation(DrillType.FOREHAND, "스플릿 스텝 후 스윙", "타점", 15),
        ),
        isFallbackReport = isFallback,
        rawModelName = "test-model",
    )

    @Test
    fun ac2_generateButtonShownWhenNoReportAndClickInvokesCallback() {
        var generateClicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                SessionCompletionDialog(
                    summary = summary,
                    aiReport = null,
                    isGeneratingAiReport = false,
                    onGenerateAiReport = { generateClicks++ },
                    onDismiss = {},
                    onNavigateToReplay = { _, _ -> }
                )
            }
        }
        composeTestRule.onNodeWithText("🤖 AI 코치 처방받기").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, generateClicks)
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("🤖 AI 코치가 5단계 운동 체인과 스윙 역학을 분석하고 있습니다...")
                .fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("🤖 AI 코치 처방 리포트").fetchSemanticsNodes().size
        )
    }

    @Test
    fun ac3_skeletonShownWhileGeneratingAndButtonHidden() {
        composeTestRule.setContent {
            MaterialTheme {
                SessionCompletionDialog(
                    summary = summary,
                    aiReport = null,
                    isGeneratingAiReport = true,
                    onGenerateAiReport = {},
                    onDismiss = {},
                    onNavigateToReplay = { _, _ -> }
                )
            }
        }
        composeTestRule.onNodeWithText("🤖 AI 코치가 5단계 운동 체인과 스윙 역학을 분석하고 있습니다...")
            .assertExists()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("🤖 AI 코치 처방받기").fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithText("🎬 리플레이 보기").assertIsDisplayed()
        composeTestRule.onNodeWithText("닫기 / 새 훈련").assertIsDisplayed()
    }

    @Test
    fun ac4_reportCardExpandedWithSummaryBadgeAndDrill() {
        composeTestRule.setContent {
            MaterialTheme {
                SessionCompletionDialog(
                    summary = summary,
                    aiReport = dummyReport(isFallback = false),
                    isGeneratingAiReport = false,
                    onGenerateAiReport = {},
                    onDismiss = {},
                    onNavigateToReplay = { _, _ -> }
                )
            }
        }
        composeTestRule.onNodeWithText("🤖 AI 코치 처방 리포트").assertExists()
        composeTestRule.onNodeWithText("✨ Gemini AI 분석").assertExists()
        composeTestRule.onNodeWithText("세션 총평 텍스트").assertExists()
        composeTestRule.onNodeWithText("스플릿 스텝 후 스윙").assertExists()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("🤖 AI 코치 처방받기").fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithText("🎬 리플레이 보기").assertIsDisplayed()
    }

    @Test
    fun replayButtonHiddenWhenSummaryHasNoVideo() {
        composeTestRule.setContent {
            MaterialTheme {
                SessionCompletionDialog(
                    summary = summary.copy(hasVideo = false),
                    aiReport = null,
                    isGeneratingAiReport = false,
                    onGenerateAiReport = {},
                    onDismiss = {},
                    onNavigateToReplay = { _, _ -> }
                )
            }
        }
        composeTestRule.onNodeWithText("🎯 포핸드 훈련 완료!").assertIsDisplayed()
        composeTestRule.onNodeWithText("닫기 / 새 훈련").assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("🎬 리플레이 보기").fetchSemanticsNodes().size
        )
    }
}
