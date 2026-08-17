package io.github.loje0611.tennisdoc.core.model

/**
 * AI 코치가 분석한 세션 종합 정밀 처방 리포트 도메인 모델.
 */
data class AiCoachReport(
    val reportId: String,
    val sessionId: String,
    val generatedAtMillis: Long,
    val overallSummary: String,
    val keyStrengths: List<String> = emptyList(),
    val primaryFlawDiagnosis: CausalFlawDiagnosis? = null,
    val actionItems: List<String> = emptyList(),
    val recommendedDrills: List<DrillRecommendation> = emptyList(),
    val isFallbackReport: Boolean = false,
    val rawModelName: String? = null,
)

/**
 * 센서-비전 융합 기반 핵심 결함 원인 분석.
 */
data class CausalFlawDiagnosis(
    val flawTitle: String,
    val observedEffect: String,
    val rootCause: String,
    val coachingCue: String,
)

/**
 * 다음 세션 추천 드릴 및 연습 목표.
 */
data class DrillRecommendation(
    val drillType: DrillType,
    val title: String,
    val focusPoint: String,
    val targetRepetitions: Int = 10,
)

/**
 * AI 코칭 스타일 / 톤 설정.
 */
enum class CoachTone {
    ENCOURAGING, // 격려 및 긍정형
    ANALYTICAL,  // 정밀 데이터 분석형
    STRICT,      // 엄격한 프로 코치형
}

/**
 * AI 코치 LLM 프로바이더.
 */
enum class LlmProvider {
    MOCK,
    GEMINI,
    OPENAI,
    LOCAL_RULE_ONLY
}
