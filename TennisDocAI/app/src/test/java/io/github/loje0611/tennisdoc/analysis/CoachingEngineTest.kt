package io.github.loje0611.tennisdoc.analysis

import io.github.loje0611.tennisdoc.core.model.SwingMetrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingEngineTest {

    private fun metrics(
        power: Int = 50, spin: Int = 50, timing: Int = 50,
        smoothness: Int = 50, stability: Int = 50, consistency: Int = 50,
    ) = SwingMetrics(power, spin, timing, smoothness, stability, consistency)

    @Test
    fun `generateComment returns non-empty for topspin without history`() {
        val result = CoachingEngine.generateComment("forehand topspin", metrics(), null)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `generateComment returns non-empty for slice without history`() {
        val result = CoachingEngine.generateComment("backhand slice", metrics(), null)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `generateComment returns non-empty for volley without history`() {
        val result = CoachingEngine.generateComment("forehand volley", metrics(), null)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `generateComment returns non-empty for unknown type`() {
        val result = CoachingEngine.generateComment("unknown shot", metrics(), null)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `delta comment highlights power improvement`() {
        val history = metrics(power = 60)
        val current = metrics(power = 80)
        val result = CoachingEngine.generateComment("forehand topspin", current, history)
        assertTrue(result.contains("파워"))
    }

    @Test
    fun `delta comment highlights stability drop`() {
        val history = metrics(stability = 80)
        val current = metrics(stability = 50)
        val result = CoachingEngine.generateComment("forehand topspin", current, history)
        assertTrue(result.contains("안정성"))
    }

    @Test
    fun `topspin warns high power low spin`() {
        val m = metrics(power = 90, spin = 30)
        val result = CoachingEngine.generateComment("forehand topspin", m, null)
        assertTrue(result.contains("스핀") || result.contains("와이퍼"))
    }

    @Test
    fun `topspin praises balanced high power and spin`() {
        val m = metrics(power = 80, spin = 80, timing = 70, smoothness = 70, stability = 70)
        val result = CoachingEngine.generateComment("forehand topspin", m, null)
        assertTrue(result.contains("완벽") || result.contains("훌륭"))
    }

    @Test
    fun `volley warns excessive power`() {
        val m = metrics(power = 80)
        val result = CoachingEngine.generateComment("forehand volley", m, null)
        assertTrue(result.contains("스윙") || result.contains("끊어"))
    }

    @Test
    fun `slice warns low spin`() {
        val m = metrics(spin = 30)
        val result = CoachingEngine.generateComment("backhand slice", m, null)
        assertTrue(result.contains("스핀") || result.contains("언더"))
    }

    @Test
    fun `no history means no delta prefix`() {
        val result = CoachingEngine.generateComment("forehand topspin", metrics(), null)
        assertFalse(result.contains("📈") || result.contains("📉") || result.contains("⏱️") || result.contains("🎯"))
    }

    @Test
    fun `stable performance with history returns maintenance comment`() {
        val m = metrics()
        val result = CoachingEngine.generateComment("forehand topspin", m, m)
        assertTrue(result.contains("유지"))
    }
}
