package io.github.loje0611.tennisdoc.core.fusion.orientation

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import kotlin.math.abs

class RacketImpactCalculator(
    private val squareThresholdDeg: Float = 8.0f
) {
    fun calculate(
        imuSamples: List<ImuDataPoint>,
        anchor: SyncAnchor
    ): RacketImpactOrientation {
        if (imuSamples.isEmpty()) {
            return RacketImpactOrientation(
                rollDeg = 0f,
                pitchDeg = 0f,
                yawDeg = 0f,
                faceState = RacketFaceState.SQUARE,
                deviationDeg = 0f
            )
        }

        val targetTs = anchor.sensorImpactTimestampMs
        val impactSample = imuSamples.minByOrNull { abs(it.timestampMs - targetTs) } ?: imuSamples.first()

        val roll = impactSample.gyroY * 0.02f
        val pitch = impactSample.gyroX * 0.02f
        val yaw = impactSample.gyroZ * 0.02f

        val deviation = roll
        val faceState = when {
            deviation > squareThresholdDeg -> RacketFaceState.OPEN
            deviation < -squareThresholdDeg -> RacketFaceState.CLOSED
            else -> RacketFaceState.SQUARE
        }

        return RacketImpactOrientation(
            rollDeg = roll,
            pitchDeg = pitch,
            yawDeg = yaw,
            faceState = faceState,
            deviationDeg = deviation
        )
    }
}
