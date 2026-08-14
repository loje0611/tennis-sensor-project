package io.github.loje0611.tennisdoc.core.fusion.sync

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.vision.analyzer.ImpactDetector
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import kotlin.math.abs
import kotlin.math.sqrt

class ImpactAnchorSynchronizer(
    private val maxAllowedOffsetMs: Long = 150L,
    private val minAccelImpactThresholdG: Float = 4.0f
) {
    /**
     * 비전 포즈 프레임과 IMU 센서 샘플을 비교하여 임팩트 앵커를 계산한다.
     */
    fun synchronize(
        poses: List<PoseFrame>,
        imuSamples: List<ImuDataPoint>,
        baseVisionTimestampMs: Long = imuSamples.firstOrNull()?.timestampMs ?: 0L,
        visionFps: Float = 30f,
        isRightHand: Boolean = true
    ): SyncAnchor {
        val visionImpact = detectVisionImpact(poses, baseVisionTimestampMs, visionFps, isRightHand)
        val sensorImpact = detectSensorImpact(imuSamples)

        if (visionImpact == null || sensorImpact == null) {
            val vTs = visionImpact?.first ?: 0L
            val sTs = sensorImpact?.first ?: 0L
            return SyncAnchor(
                visionImpactTimestampMs = vTs,
                sensorImpactTimestampMs = sTs,
                timeOffsetMs = if (visionImpact != null && sensorImpact != null) sTs - vTs else 0L,
                confidence = 0.0f,
                isSynchronized = false
            )
        }

        val (visionTs, _) = visionImpact
        val (sensorTs, peakAccel) = sensorImpact
        val timeOffsetMs = sensorTs - visionTs
        val absOffset = abs(timeOffsetMs)

        if (absOffset > maxAllowedOffsetMs) {
            return SyncAnchor(
                visionImpactTimestampMs = visionTs,
                sensorImpactTimestampMs = sensorTs,
                timeOffsetMs = timeOffsetMs,
                confidence = 0.0f,
                isSynchronized = false
            )
        }

        val proximityScore = (1.0f - (absOffset.toFloat() / maxAllowedOffsetMs.toFloat())).coerceIn(0f, 1f)
        val intensityScore = (peakAccel / 10.0f).coerceIn(0.6f, 1.0f)
        val confidence = (0.5f * proximityScore + 0.5f * intensityScore).coerceIn(0f, 1f)
        val isSynchronized = absOffset <= 100L

        return SyncAnchor(
            visionImpactTimestampMs = visionTs,
            sensorImpactTimestampMs = sensorTs,
            timeOffsetMs = timeOffsetMs,
            confidence = confidence,
            isSynchronized = isSynchronized
        )
    }

    fun detectVisionImpact(
        poses: List<PoseFrame>,
        baseVisionTimestampMs: Long = 0L,
        fps: Float = 30f,
        isRightHand: Boolean = true
    ): Pair<Long, Float>? {
        if (poses.size < 2) return null

        val result = ImpactDetector.detectImpactFrames(poses, fps = fps, isRightHand = isRightHand)
        if (result.impactFrames.isEmpty()) return null

        val peakFrameIndex = result.impactFrames.first()
        val peakVelocity = result.velocities.getOrNull(peakFrameIndex) ?: 0f
        val timestampMs = baseVisionTimestampMs + (peakFrameIndex * 1000f / fps).toLong()

        return Pair(timestampMs, peakVelocity)
    }

    fun detectSensorImpact(imuSamples: List<ImuDataPoint>): Pair<Long, Float>? {
        if (imuSamples.isEmpty()) return null

        var maxAccelMag = 0f
        var peakTimestampMs: Long? = null

        for (sample in imuSamples) {
            val mag = sqrt(sample.accelX * sample.accelX + sample.accelY * sample.accelY + sample.accelZ * sample.accelZ)
            if (mag >= minAccelImpactThresholdG && mag > maxAccelMag) {
                maxAccelMag = mag
                peakTimestampMs = sample.timestampMs
            }
        }

        return if (peakTimestampMs != null) {
            Pair(peakTimestampMs, maxAccelMag)
        } else {
            null
        }
    }
}
