package io.github.loje0611.tennisdoc.core.coach.engine

import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CausalFlawDiagnosis
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.DrillRecommendation
import java.util.UUID

class LocalRuleBasedCoachEngine {
    fun generateFallbackReport(
        context: SessionPrescriptionContext,
        tone: CoachTone = CoachTone.ENCOURAGING
    ): AiCoachReport {
        val strengths = mutableListOf<String>()
        if (context.squareFaceRatePercent >= 70) strengths.add("우수한 임팩트 정타율")
        if (context.averageEnergyEfficiency >= 80f) strengths.add("높은 에너지 전송 효율")

        val summary = buildString {
            if (tone == CoachTone.ENCOURAGING) append("훌륭한 노력입니다! ")
            else if (tone == CoachTone.STRICT) append("결과에 집중해야 합니다. ")
            
            append("세션 전반적으로 ")
            if (context.averageEnergyEfficiency >= 80f) {
                append("운동 체인이 부드럽게 연결되고 있습니다.")
            } else {
                append("에너지 손실이 관찰됩니다.")
            }
        }

        val primaryFlawDiagnosis = when (context.primaryFlawTag) {
            "EARLY_BODY_OPEN" -> CausalFlawDiagnosis(
                flawTitle = "EARLY_BODY_OPEN",
                observedEffect = "상체 조기 회전으로 타점이 밀리고 페이스가 열림",
                rootCause = "골반보다 상체가 먼저 회전하여 체인 타이밍 어긋남",
                coachingCue = "하체가 리드할 때까지 상체 열림을 참으세요."
            )
            "FACE_OPEN" -> CausalFlawDiagnosis(
                flawTitle = "FACE_OPEN",
                observedEffect = "라켓 페이스 열림으로 인한 오프센터 타격",
                rootCause = "그립 불안정 또는 임팩트 직전 손목 젖혀짐",
                coachingCue = "임팩트 순간 라켓면 스퀘어 유지에 집중하세요."
            )
            "FACE_CLOSED", "LATE_CONTACT" -> CausalFlawDiagnosis(
                flawTitle = context.primaryFlawTag ?: "LATE_CONTACT",
                observedEffect = "타점이 몸보다 너무 뒤에서 형성됨",
                rootCause = "준비 자세 늦음 또는 스윙 궤적 축소",
                coachingCue = "타점을 몸 앞쪽에 두고 스윙을 뻗어주세요."
            )
            "POWER_LEAK", "CHAIN_TIMING_DELAY" -> CausalFlawDiagnosis(
                flawTitle = context.primaryFlawTag ?: "POWER_LEAK",
                observedEffect = "운동 체인 분절 간 가속 전달 누수",
                rootCause = "각 관절의 릴리즈 타이밍 불일치",
                coachingCue = "손목과 라켓의 릴리즈를 자연스럽게 이어가세요."
            )
            "CLEAN_STRIKE" -> CausalFlawDiagnosis(
                flawTitle = "CLEAN_STRIKE",
                observedEffect = "이상적인 체인 타이밍 및 정타",
                rootCause = "올바른 생체역학적 폼 유지",
                coachingCue = "현재의 좋은 리듬과 밸런스를 계속 유지하세요!"
            )
            else -> context.primaryFlawTag?.let {
                CausalFlawDiagnosis(
                    flawTitle = it,
                    observedEffect = "비정상적인 스윙 패턴 감지",
                    rootCause = "복합적인 밸런스 불균형",
                    coachingCue = "전반적인 밸런스 유지에 신경 쓰세요."
                )
            }
        }

        val drills = listOf(
            DrillRecommendation(
                drillType = context.drillType,
                title = "로컬 맞춤 기본 드릴",
                focusPoint = primaryFlawDiagnosis?.coachingCue ?: "밸런스 유지",
                targetRepetitions = 15
            )
        )

        return AiCoachReport(
            reportId = UUID.randomUUID().toString(),
            sessionId = context.sessionId,
            generatedAtMillis = System.currentTimeMillis(),
            overallSummary = summary,
            keyStrengths = strengths,
            primaryFlawDiagnosis = primaryFlawDiagnosis,
            actionItems = listOf("자세 교정", "타이밍 훈련"),
            recommendedDrills = drills,
            isFallbackReport = true,
            rawModelName = "local-rule-engine"
        )
    }
}
