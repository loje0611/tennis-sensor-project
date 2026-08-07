package io.github.loje0611.tennisdoc.core.model

/**
 * 구종별 스윙 품질 지표와 이력 지표를 바탕으로 코칭 코멘트를 생성하는 인터페이스.
 */
interface CoachingCommentGenerator {
    fun generateComment(type: String, current: SwingMetrics, history: SwingMetrics?): String
}
