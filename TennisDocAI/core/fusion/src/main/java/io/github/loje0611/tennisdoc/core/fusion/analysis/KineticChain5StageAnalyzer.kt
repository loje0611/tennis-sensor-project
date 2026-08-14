package io.github.loje0611.tennisdoc.core.fusion.analysis

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.vision.analyzer.ImpactDetector
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import kotlin.math.max
import kotlin.math.sqrt

class KineticChain5StageAnalyzer(
    private val visionFps: Float = 30f,
    private val isRightHand: Boolean = true
) {

    fun analyze(
        poses: List<PoseFrame>,
        imuSamples: List<ImuDataPoint>,
        anchor: SyncAnchor
    ): KineticChain5Stage {
        if (!anchor.isSynchronized || poses.size < 2 || imuSamples.isEmpty()) {
            return createFallbackChain()
        }

        // 1. Vision Peaks (Hip, Shoulder, Wrist)
        val hipIdx = if (isRightHand) 24 else 23
        val shoulderIdx = if (isRightHand) 12 else 11
        val wristIdx = if (isRightHand) 16 else 15

        val velHipRaw = ImpactDetector.calculateVelocity(poses, hipIdx, visionFps)
        val velShoulderRaw = ImpactDetector.calculateVelocity(poses, shoulderIdx, visionFps)
        val velWristRaw = ImpactDetector.calculateVelocity(poses, wristIdx, visionFps)

        if (velHipRaw.isEmpty() || velShoulderRaw.isEmpty() || velWristRaw.isEmpty()) {
            return createFallbackChain()
        }

        val velHip = velHipRaw.map { if (it.isNaN()) 0.0f else it }
        val velShoulder = velShoulderRaw.map { if (it.isNaN()) 0.0f else it }
        val velWrist = velWristRaw.map { if (it.isNaN()) 0.0f else it }

        val maxHip = velHip.maxOrNull() ?: 0.0f
        val peakHipFrame = if (maxHip > 0f) velHip.indexOf(maxHip).coerceAtLeast(0) else 0

        val maxShoulder = velShoulder.maxOrNull() ?: 0.0f
        val peakShoulderFrame = if (maxShoulder > 0f) velShoulder.indexOf(maxShoulder).coerceAtLeast(0) else 0

        val maxWrist = velWrist.maxOrNull() ?: 0.0f
        val peakWristFrame = if (maxWrist > 0f) velWrist.indexOf(maxWrist).coerceAtLeast(0) else 0

        val msPerFrame = 1000.0 / visionFps
        val baseVisionTs = anchor.visionImpactTimestampMs - (peakWristFrame * msPerFrame).toLong()

        val tHip = baseVisionTs + (peakHipFrame * msPerFrame).toLong()
        val tShoulder = baseVisionTs + (peakShoulderFrame * msPerFrame).toLong()
        val tWrist = baseVisionTs + (peakWristFrame * msPerFrame).toLong()

        // 2. Sensor Peaks (Racket Gyro, Impact Accel)
        var maxGyroMag = 0f
        var peakRacketSensorTs: Long = imuSamples.first().timestampMs

        var maxAccelMag = 0f
        var peakImpactSensorTs: Long = imuSamples.first().timestampMs

        for (sample in imuSamples) {
            val gyroMag = sqrt(sample.gyroX * sample.gyroX + sample.gyroY * sample.gyroY + sample.gyroZ * sample.gyroZ)
            if (gyroMag > maxGyroMag) {
                maxGyroMag = gyroMag
                peakRacketSensorTs = sample.timestampMs
            }

            val accelMag = sqrt(sample.accelX * sample.accelX + sample.accelY * sample.accelY + sample.accelZ * sample.accelZ)
            if (accelMag > maxAccelMag) {
                maxAccelMag = accelMag
                peakImpactSensorTs = sample.timestampMs
            }
        }

        // Align sensor timestamps to common vision time axis
        val tRacket = peakRacketSensorTs - anchor.timeOffsetMs
        val tImpact = peakImpactSensorTs - anchor.timeOffsetMs

        // 3. Build Stages
        val delayShoulder = tShoulder - tHip
        val delayWrist = tWrist - tShoulder
        val delayRacket = tRacket - tWrist
        val delayImpact = tImpact - tRacket

        val stages = listOf(
            KineticStage(KineticStageType.HIP, tHip, maxHip, 0L),
            KineticStage(KineticStageType.SHOULDER, tShoulder, maxShoulder, delayShoulder),
            KineticStage(KineticStageType.WRIST, tWrist, maxWrist, delayWrist),
            KineticStage(KineticStageType.RACKET, tRacket, maxGyroMag, delayRacket),
            KineticStage(KineticStageType.IMPACT, tImpact, maxAccelMag, delayImpact)
        )

        // 4. Sequentiality and Efficiency
        val isSequential = (tHip <= tShoulder) && (tShoulder <= tWrist) && (tWrist <= tRacket) && (tRacket <= tImpact)
        val totalDurationMs = max(0L, tImpact - tHip)

        val efficiency = calculateEfficiency(isSequential, delayShoulder, delayWrist, delayRacket, delayImpact)

        return KineticChain5Stage(
            stages = stages,
            isSequential = isSequential,
            totalDurationMs = totalDurationMs,
            energyTransferEfficiency = efficiency
        )
    }

    private fun calculateEfficiency(
        isSequential: Boolean,
        delayShoulder: Long,
        delayWrist: Long,
        delayRacket: Long,
        delayImpact: Long
    ): Float {
        val baseScore = if (isSequential) 40.0f else 0.0f

        val score1 = scoreDelay(delayShoulder, 15L, 80L)
        val score2 = scoreDelay(delayWrist, 15L, 80L)
        val score3 = scoreDelay(delayRacket, 10L, 60L)
        val score4 = scoreDelay(delayImpact, 5L, 40L)

        return (baseScore + score1 + score2 + score3 + score4).coerceIn(0.0f, 100.0f)
    }

    private fun scoreDelay(delay: Long, minAllowed: Long, maxAllowed: Long): Float {
        if (delay in minAllowed..maxAllowed) {
            return 15.0f
        }
        if (delay < 0L) {
            return 0.0f
        }
        if (delay < minAllowed) {
            val ratio = delay.toFloat() / minAllowed.toFloat()
            return (15.0f * ratio).coerceIn(0.0f, 15.0f)
        }
        // delay > maxAllowed
        val excess = (delay - maxAllowed).toFloat()
        val penalty = excess / maxAllowed.toFloat()
        return (15.0f * (1.0f - penalty)).coerceIn(0.0f, 15.0f)
    }

    private fun createFallbackChain(): KineticChain5Stage {
        val stages = listOf(
            KineticStage(KineticStageType.HIP, 0L, 0f, 0L),
            KineticStage(KineticStageType.SHOULDER, 0L, 0f, 0L),
            KineticStage(KineticStageType.WRIST, 0L, 0f, 0L),
            KineticStage(KineticStageType.RACKET, 0L, 0f, 0L),
            KineticStage(KineticStageType.IMPACT, 0L, 0f, 0L)
        )
        return KineticChain5Stage(
            stages = stages,
            isSequential = false,
            totalDurationMs = 0L,
            energyTransferEfficiency = 0.0f
        )
    }
}
