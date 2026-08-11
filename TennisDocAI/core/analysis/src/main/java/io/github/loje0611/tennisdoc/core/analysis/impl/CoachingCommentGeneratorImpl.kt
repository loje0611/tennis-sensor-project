package io.github.loje0611.tennisdoc.core.analysis.impl

import io.github.loje0611.tennisdoc.core.analysis.CoachingEngine
import io.github.loje0611.tennisdoc.core.model.CoachingCommentGenerator
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachingCommentGeneratorImpl @Inject constructor() : CoachingCommentGenerator {
    override fun generateComment(
        type: String,
        current: SwingMetrics,
        history: SwingMetrics?,
    ): String {
        return CoachingEngine.generateComment(type, current, history)
    }
}
