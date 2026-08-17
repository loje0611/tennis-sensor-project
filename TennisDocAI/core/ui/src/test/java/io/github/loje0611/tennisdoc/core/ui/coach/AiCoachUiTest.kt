package io.github.loje0611.tennisdoc.core.ui.coach

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CausalFlawDiagnosis
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.DrillRecommendation
import io.github.loje0611.tennisdoc.core.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AiCoachUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createDummyReport(isFallback: Boolean): AiCoachReport {
        return AiCoachReport(
            reportId = UUID.randomUUID().toString(),
            sessionId = "sess-test",
            generatedAtMillis = 10000L,
            overallSummary = "Great performance today.",
            keyStrengths = listOf("우수한 임팩트 정타율", "높은 에너지 효율"),
            primaryFlawDiagnosis = CausalFlawDiagnosis(
                flawTitle = "EARLY_BODY_OPEN",
                observedEffect = "상체 조기 회전",
                rootCause = "타이밍 어긋남",
                coachingCue = "하체 리드 대기",
            ),
            actionItems = listOf("자세 훈련", "타점 맞추기"),
            recommendedDrills = listOf(
                DrillRecommendation(DrillType.FOREHAND, "스플릿 스텝 후 스윙", "타점", 15),
            ),
            isFallbackReport = isFallback,
            rawModelName = "test-model",
        )
    }

    @Test
    fun testAiCoachReportCard_Gemini_DisplaysAllSections() {
        val report = createDummyReport(isFallback = false)

        composeTestRule.setContent {
            AiCoachReportCard(report = report)
        }

        composeTestRule.onNodeWithText("🤖 AI 코치 처방 리포트").assertExists()
        composeTestRule.onNodeWithText("✨ Gemini AI 분석").assertExists()
        composeTestRule.onNodeWithText("Great performance today.").assertExists()
        composeTestRule.onNodeWithText("우수한 임팩트 정타율").assertExists()
        composeTestRule.onNodeWithText("높은 에너지 효율").assertExists()
        composeTestRule.onNodeWithText("상체 조기 회전").assertExists()
        composeTestRule.onNodeWithText("타이밍 어긋남").assertExists()
        composeTestRule.onNodeWithText("하체 리드 대기").assertExists()
        composeTestRule.onNodeWithText("자세 훈련").assertExists()
        composeTestRule.onNodeWithText("타점 맞추기").assertExists()
        composeTestRule.onNodeWithText("스플릿 스텝 후 스윙").assertExists()
        composeTestRule.onNodeWithText("15회").assertExists()
        composeTestRule.onNodeWithText("타점").assertExists()
    }

    @Test
    fun testAiCoachReportCard_Fallback_DisplaysFallbackBadge() {
        val report = createDummyReport(isFallback = true)

        composeTestRule.setContent {
            AiCoachReportCard(report = report)
        }

        composeTestRule.onNodeWithText("⚡ 로컬 룰 엔진 분석").assertExists()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("✨ Gemini AI 분석").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun ac2_causalDiagnosisCardShowsThreeStages() {
        composeTestRule.setContent {
            CausalDiagnosisCard(
                diagnosis = CausalFlawDiagnosis(
                    flawTitle = "FACE_OPEN",
                    observedEffect = "임팩트 시 라켓 페이스 +14° 열림",
                    rootCause = "골반 대비 어깨 조기 회전",
                    coachingCue = "라켓 헤드를 뒤에 두고 하체 리드",
                ),
            )
        }
        composeTestRule.onNodeWithText("관측된 현상").assertIsDisplayed()
        composeTestRule.onNodeWithText("근본 원인").assertIsDisplayed()
        composeTestRule.onNodeWithText("코칭 큐").assertIsDisplayed()
        composeTestRule.onNodeWithText("임팩트 시 라켓 페이스 +14° 열림").assertIsDisplayed()
        composeTestRule.onNodeWithText("골반 대비 어깨 조기 회전").assertIsDisplayed()
        composeTestRule.onNodeWithText("라켓 헤드를 뒤에 두고 하체 리드").assertIsDisplayed()
    }

    @Test
    fun ac1_nullDiagnosisOmitsCausalCardAndEmptyListsStayCompact() {
        val report = AiCoachReport(
            reportId = "r1",
            sessionId = "s1",
            generatedAtMillis = 0L,
            overallSummary = "요약만",
            keyStrengths = emptyList(),
            primaryFlawDiagnosis = null,
            actionItems = emptyList(),
            recommendedDrills = emptyList(),
            isFallbackReport = false,
        )
        composeTestRule.setContent { AiCoachReportCard(report = report) }
        composeTestRule.onNodeWithText("요약만").assertExists()
        composeTestRule.onNodeWithText("✨ Gemini AI 분석").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithText("관측된 현상").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("📋 집중 과제").fetchSemanticsNodes().size)
    }

    @Test
    fun testCoachToneSelector_SelectsTone() {
        var selected = CoachTone.ENCOURAGING

        composeTestRule.setContent {
            CoachToneSelector(
                selectedTone = selected,
                onToneSelected = { selected = it },
            )
        }

        composeTestRule.onNodeWithText("📊 분석형").performClick()
        assertEquals(CoachTone.ANALYTICAL, selected)
    }

    @Test
    fun ac4_toneSelectorInvokesEncouragingAndStrict() {
        var selected by mutableStateOf(CoachTone.ANALYTICAL)
        composeTestRule.setContent {
            CoachToneSelector(selectedTone = selected, onToneSelected = { selected = it })
        }
        composeTestRule.onNodeWithText("🌱 격려형").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(CoachTone.ENCOURAGING, selected)
        composeTestRule.onNodeWithText("🎯 엄격형").performClick()
        composeTestRule.waitForIdle()
        assertEquals(CoachTone.STRICT, selected)
    }

    @Test
    fun testAiCoachLoadingSkeleton_DisplaysProperly() {
        composeTestRule.setContent {
            AiCoachLoadingSkeleton()
        }

        composeTestRule.onNodeWithText("🤖 AI 코치가 5단계 운동 체인과 스윙 역학을 분석하고 있습니다...")
            .assertExists()
    }
}
