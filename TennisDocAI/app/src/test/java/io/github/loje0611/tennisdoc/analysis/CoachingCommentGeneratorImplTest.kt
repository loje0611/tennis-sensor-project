package io.github.loje0611.tennisdoc.analysis

import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import org.junit.Assert.assertTrue
import org.junit.Test

/** AC-11: 위임 구현이 CoachingEngine까지 도달하는지 검증. */
class CoachingCommentGeneratorImplTest {

    private val generator = CoachingCommentGeneratorImpl()

    @Test
    fun generateCommentDelegatesToCoachingEngine() {
        val metrics = SwingMetrics(
            power = 80,
            spin = 20,
            timing = 70,
            smoothness = 70,
            stability = 70,
            consistency = 70,
        )
        val result = generator.generateComment("forehand topspin", metrics, history = null)
        assertTrue(result.isNotBlank())
        assertTrue(result.contains("스핀") || result.contains("와이퍼"))
    }
}
