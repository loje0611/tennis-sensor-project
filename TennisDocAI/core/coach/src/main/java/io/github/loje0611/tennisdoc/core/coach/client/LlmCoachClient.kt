package io.github.loje0611.tennisdoc.core.coach.client

import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CausalFlawDiagnosis
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.DrillRecommendation
import java.util.UUID

interface LlmCoachClient {
    suspend fun generateReport(
        context: SessionPrescriptionContext,
        tone: CoachTone = CoachTone.ENCOURAGING
    ): Result<AiCoachReport>
}

class MockLlmCoachClient : LlmCoachClient {
    override suspend fun generateReport(
        context: SessionPrescriptionContext,
        tone: CoachTone
    ): Result<AiCoachReport> {
        val summary = if (context.averageEnergyEfficiency > 80f) {
            "에너지 전송 효율이 매우 우수한 세션입니다. 기본기가 탄탄하며 꾸준히 향상되고 있습니다."
        } else {
            "운동 체인의 연결이 일부 끊겨 에너지 손실이 발생하고 있습니다. 기본 폼 교정이 필요합니다."
        }
        
        val flaw = context.primaryFlawTag?.let { flawTag ->
            CausalFlawDiagnosis(
                flawTitle = flawTag,
                observedEffect = "스윙 타이밍이 맞지 않아 페이스 정타율이 ${context.squareFaceRatePercent}%로 낮아짐",
                rootCause = "운동 체인 불균형에 따른 어깨와 팔의 협응 부족",
                coachingCue = "하체 회전을 먼저 시작하고 팔을 늦게 따라오도록 의식하세요."
            )
        }
        
        val report = AiCoachReport(
            reportId = UUID.randomUUID().toString(),
            sessionId = context.sessionId,
            generatedAtMillis = System.currentTimeMillis(),
            overallSummary = "Mock 톤(${tone.name}): $summary",
            keyStrengths = listOf("준수한 반응 속도", "에너지 효율 ${context.averageEnergyEfficiency}%"),
            primaryFlawDiagnosis = flaw,
            actionItems = listOf("하체 턴 연습", "타점 맞추기 집중"),
            recommendedDrills = listOf(
                DrillRecommendation(
                    drillType = context.drillType,
                    title = "스텝-스플릿 후 기본 스윙",
                    focusPoint = "골반 회전과 라켓면 스퀘어 유지",
                    targetRepetitions = 20
                )
            ),
            isFallbackReport = false,
            rawModelName = "mock-model"
        )
        
        return Result.success(report)
    }
}
