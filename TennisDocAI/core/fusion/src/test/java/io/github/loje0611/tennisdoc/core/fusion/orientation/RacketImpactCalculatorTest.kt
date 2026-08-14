package io.github.loje0611.tennisdoc.core.fusion.orientation

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import org.junit.Assert.assertEquals
import org.junit.Test

class RacketImpactCalculatorTest {

    private val calculator = RacketImpactCalculator(squareThresholdDeg = 8.0f)

    @Test
    fun `open face is detected when roll deviation exceeds positive threshold`() {
        val anchor = SyncAnchor(1000L, 1000L, 0L, 0.9f, true)
        val imu = listOf(
            ImuDataPoint(1000L, 10f, 0f, 0f, 0f, 600f, 0f) // gyroY 600 * 0.02 = 12.0 deg > 8.0
        )
        val result = calculator.calculate(imu, anchor)
        assertEquals(RacketFaceState.OPEN, result.faceState)
        assertEquals(12.0f, result.deviationDeg, 0.001f)
    }

    @Test
    fun `closed face is detected when roll deviation exceeds negative threshold`() {
        val anchor = SyncAnchor(1000L, 1000L, 0L, 0.9f, true)
        val imu = listOf(
            ImuDataPoint(1000L, 10f, 0f, 0f, 0f, -600f, 0f) // gyroY -600 * 0.02 = -12.0 deg < -8.0
        )
        val result = calculator.calculate(imu, anchor)
        assertEquals(RacketFaceState.CLOSED, result.faceState)
        assertEquals(-12.0f, result.deviationDeg, 0.001f)
    }

    @Test
    fun `square face is detected when deviation is within threshold`() {
        val anchor = SyncAnchor(1000L, 1000L, 0L, 0.9f, true)
        val imu = listOf(
            ImuDataPoint(1000L, 10f, 0f, 0f, 0f, 100f, 0f) // gyroY 100 * 0.02 = 2.0 deg
        )
        val result = calculator.calculate(imu, anchor)
        assertEquals(RacketFaceState.SQUARE, result.faceState)
        assertEquals(2.0f, result.deviationDeg, 0.001f)
    }
}
