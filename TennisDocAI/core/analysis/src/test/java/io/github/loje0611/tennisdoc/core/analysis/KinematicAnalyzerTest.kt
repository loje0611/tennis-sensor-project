package io.github.loje0611.tennisdoc.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

class KinematicAnalyzerTest {

    private lateinit var analyzer: KinematicAnalyzer

    @Before
    fun setUp() {
        analyzer = KinematicAnalyzer()
    }

    private fun sample(ax: Float = 0f, ay: Float = 0f, az: Float = 9.8f,
                       gx: Float = 0f, gy: Float = 0f, gz: Float = 0f) =
        floatArrayOf(ax, ay, az, gx, gy, gz)

    @Test
    fun `empty samples return zeroed metrics with default consistency of 50`() {
        val result = analyzer.analyze(emptyList())
        assertEquals(0, result.power)
        assertEquals(0, result.spin)
        assertEquals(0, result.timing)
        assertEquals(0, result.smoothness)
        assertEquals(0, result.stability)
        assertEquals(50, result.consistency)
    }

    @Test
    fun `all metrics are in 0-100 range`() {
        val samples = List(100) { i ->
            val t = i / 50f * Math.PI.toFloat()
            sample(
                ax = 20f * sin(t), ay = 10f * sin(t * 2),
                az = 9.8f + 5f * sin(t),
                gx = 100f * sin(t), gy = 50f * sin(t),
                gz = 500f * sin(t * 3),
            )
        }
        val result = analyzer.analyze(samples)
        listOf(result.power, result.spin, result.timing, result.smoothness,
            result.stability, result.consistency).forEach { metric ->
            assertTrue("Metric $metric out of range", metric in 0..100)
        }
    }

    @Test
    fun `high acceleration yields high power`() {
        val mild = List(100) { sample(ax = 2f) }
        val strong = List(100) { sample(ax = 30f) }
        val mildResult = analyzer.analyze(mild)
        val strongResult = analyzer.analyze(strong)
        assertTrue("Strong power (${strongResult.power}) should > mild (${mildResult.power})",
            strongResult.power > mildResult.power)
    }

    @Test
    fun `high gyro Z yields high spin`() {
        val lowSpin = List(100) { sample(gz = 50f) }
        val highSpin = List(100) { sample(gz = 1500f) }
        val lowResult = analyzer.analyze(lowSpin)
        val highResult = analyzer.analyze(highSpin)
        assertTrue("High spin (${highResult.spin}) should > low (${lowResult.spin})",
            highResult.spin > lowResult.spin)
    }

    @Test
    fun `smooth signal yields high smoothness`() {
        val smooth = List(100) { i -> sample(ax = (i.toFloat() / 10f)) }
        val jagged = List(100) { i -> sample(ax = if (i % 2 == 0) 30f else 0f) }
        val smoothResult = analyzer.analyze(smooth)
        val jaggedResult = analyzer.analyze(jagged)
        assertTrue("Smooth smoothness (${smoothResult.smoothness}) should > jagged (${jaggedResult.smoothness})",
            smoothResult.smoothness > jaggedResult.smoothness)
    }

    @Test
    fun `single sample returns valid metrics`() {
        val result = analyzer.analyze(listOf(sample()))
        listOf(result.power, result.spin, result.timing, result.smoothness,
            result.stability, result.consistency).forEach { metric ->
            assertTrue("Metric $metric out of range", metric in 0..100)
        }
    }

    @Test
    fun `separate instances have independent calibration`() {
        val defaultAnalyzer = KinematicAnalyzer()
        val customAnalyzer = KinematicAnalyzer(powerMax = 10f, spinMax = 500f)

        val samples = List(100) { sample(ax = 15f, gz = 300f) }
        val defaultResult = defaultAnalyzer.analyze(samples)
        val customResult = customAnalyzer.analyze(samples)

        assertTrue(
            "Narrower powerMax should yield higher power score",
            customResult.power >= defaultResult.power,
        )
    }
}
