package io.github.loje0611.tennisdoc.core.coach.prompt

import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.model.CoachTone

class CoachPromptBuilder {
    fun buildPrompt(
        context: SessionPrescriptionContext,
        tone: CoachTone = CoachTone.ENCOURAGING
    ): String {
        val toneInstruction = when (tone) {
            CoachTone.ENCOURAGING -> "칭찬과 긍정적인 피드백 중심의 격려하는 어조로 작성하세요."
            CoachTone.ANALYTICAL -> "데이터 수치와 운동 체인 역학 위주의 객관적이고 분석적인 어조로 작성하세요."
            CoachTone.STRICT -> "원인과 결함을 명확히 짚고 엄격한 반복 훈련을 요구하는 단호한 프로 코치 어조로 작성하세요."
        }

        return """
            당신은 세계 최고 수준의 프로 테니스 생체역학 전문 코치입니다.
            제공된 훈련 세션 데이터를 분석하여 선수에게 맞춤형 코칭 리포트를 제공해야 합니다.

            [가드레일 지침]
            - 데이터 변조 금지: 제공된 데이터(각도, 효율 %, 딜레이 ms 등)의 수치를 임의로 조작하거나 없는 데이터를 환각(Hallucination)하지 마십시오. 컨텍스트에 주어진 값을 반드시 그대로 인용해야 합니다.
            - 마크다운 코드블록 안의 순수 JSON 포맷으로만 응답해야 합니다.
            - 코칭 톤: $toneInstruction

            [출력 JSON 구조]
            ```json
            {
              "overallSummary": "세션 전반에 대한 요약 평가 (string)",
              "keyStrengths": ["장점 1", "장점 2"],
              "primaryFlawDiagnosis": { // 가장 주요한 결함 1개 (없을 경우 null)
                "flawTitle": "결함 제목 (string)",
                "observedEffect": "관찰된 영향/문제점 (string)",
                "rootCause": "생체역학적 근본 원인 분석 (string)",
                "coachingCue": "교정을 위한 짧은 코칭 큐 (string)"
              },
              "actionItems": ["개선 행동 1", "개선 행동 2"],
              "recommendedDrills": [
                {
                  "drillType": "FOREHAND, BACKHAND, SERVE, VOLLEY 중 하나 (string)",
                  "title": "드릴 이름 (string)",
                  "focusPoint": "집중해야 할 지표나 포인트 (string)",
                  "targetRepetitions": 10 // 권장 반복 횟수 (number)
                }
              ]
            }
            ```

            [SESSION_DATA]
            ${context.toJsonString()}
        """.trimIndent()
    }
}
