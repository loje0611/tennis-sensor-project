package io.github.loje0611.tennisdoc.coach

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService
import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContextBuilder
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.FusionDiagnosis
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.LlmProvider
import io.github.loje0611.tennisdoc.feature.lab.ui.SessionCompletionDialog
import io.github.loje0611.tennisdoc.feature.lab.ui.SessionCompletionSummary
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Phase4EndToEndIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun ac5_emptyApiKeyProducesFallbackReportPersistedAndRenderedInLabDialog() = runTest {
        val stages = listOf(
            KineticStage(KineticStageType.HIP, 1000L, 10f),
            KineticStage(KineticStageType.SHOULDER, 1030L, 15f, 30L),
            KineticStage(KineticStageType.WRIST, 1060L, 20f, 30L),
            KineticStage(KineticStageType.RACKET, 1090L, 1500f, 30L),
            KineticStage(KineticStageType.IMPACT, 1110L, 25f, 20L),
        )
        val swing = FusedSwing(
            swingId = "e2e-swing",
            sessionId = "e2e-session",
            drillType = DrillType.FOREHAND,
            anchor = SyncAnchor(1000L, 1000L, 0L, 0.9f, true),
            kineticChain = KineticChain5Stage(stages, true, 110L, 90f),
            racketImpact = RacketImpactOrientation(0f, 0f, 0f, RacketFaceState.SQUARE, 0f),
            visionPoses = emptyList(),
            imuSamples = emptyList(),
            diagnosis = FusionDiagnosis(
                diagnosisTags = listOf("CLEAN_STRIKE"),
                primaryCause = "정상 스윙",
                coachingFeedback = "훌륭한 임팩트입니다.",
                causalExplanation = "운동 체인이 순차적으로 올바르게 전달되었습니다.",
            ),
        )

        val context = SessionPrescriptionContextBuilder().buildContext(
            sessionId = "e2e-session",
            drillType = DrillType.FOREHAND,
            swings = listOf(swing),
            baseline = null,
            durationSeconds = 120L,
        )
        val report = CompositeAiCoachService().createReport(
            context,
            provider = LlmProvider.GEMINI,
            apiKey = null,
            tone = CoachTone.STRICT,
        )
        assertTrue(report.isFallbackReport)
        assertTrue(report.overallSummary.startsWith("결과에 집중해야 합니다."))

        val persisted = JSONObject()
            .put("reportId", report.reportId)
            .put("sessionId", report.sessionId)
            .put("overallSummary", report.overallSummary)
            .put("isFallbackReport", report.isFallbackReport)
            .toString()
        assertTrue(persisted.contains(report.overallSummary))
        assertEquals("e2e-session", JSONObject(persisted).getString("sessionId"))

        val summary = SessionCompletionSummary(
            sessionId = "e2e-session",
            drillName = "포핸드",
            totalSwingCount = 1,
            durationSeconds = 120L,
            squareRatePercent = 100,
            averageEnergyEfficiency = 90f,
        )
        composeTestRule.setContent {
            MaterialTheme {
                SessionCompletionDialog(
                    summary = summary,
                    aiReport = report,
                    isGeneratingAiReport = false,
                    onGenerateAiReport = {},
                    onDismiss = {},
                    onNavigateToReplay = { _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithText("🤖 AI 코치 처방 리포트").assertExists()
        composeTestRule.onNodeWithText("⚡ 로컬 룰 엔진 분석").assertExists()
        composeTestRule.onNodeWithText(report.overallSummary).assertExists()
    }
}
