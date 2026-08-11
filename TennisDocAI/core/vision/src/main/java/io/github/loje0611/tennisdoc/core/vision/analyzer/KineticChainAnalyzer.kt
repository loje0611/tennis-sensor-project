package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.JointVelocities
import io.github.loje0611.tennisdoc.core.vision.model.KineticChainResult
import io.github.loje0611.tennisdoc.core.vision.model.PeakFrames
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.TimingMs

object KineticChainAnalyzer {

    fun analyzeKineticChain(
        poseFrames: List<PoseFrame>,
        fps: Float = 30f,
        isRightHand: Boolean = true
    ): KineticChainResult? {
        if (poseFrames.size < 2) {
            return null
        }

        val hipIdx = if (isRightHand) 24 else 23
        val shoulderIdx = if (isRightHand) 12 else 11
        val wristIdx = if (isRightHand) 16 else 15

        val velHipRaw = ImpactDetector.calculateVelocity(poseFrames, hipIdx, fps)
        val velShoulderRaw = ImpactDetector.calculateVelocity(poseFrames, shoulderIdx, fps)
        val velWristRaw = ImpactDetector.calculateVelocity(poseFrames, wristIdx, fps)

        if (velHipRaw.isEmpty() || velShoulderRaw.isEmpty() || velWristRaw.isEmpty()) {
            return null
        }

        val velHip = velHipRaw.map { if (it.isNaN()) 0.0f else it }
        val velShoulder = velShoulderRaw.map { if (it.isNaN()) 0.0f else it }
        val velWrist = velWristRaw.map { if (it.isNaN()) 0.0f else it }

        val peakHip = velHip.indexOfFirst { it == velHip.maxOrNull() }.takeIf { it >= 0 } ?: 0
        val peakShoulder = velShoulder.indexOfFirst { it == velShoulder.maxOrNull() }.takeIf { it >= 0 } ?: 0
        val peakWrist = velWrist.indexOfFirst { it == velWrist.maxOrNull() }.takeIf { it >= 0 } ?: 0

        val msPerFrame = 1000.0 / fps
        val hipToShoulderMs = (peakShoulder - peakHip) * msPerFrame
        val shoulderToWristMs = (peakWrist - peakShoulder) * msPerFrame

        val isCorrectChain = (peakHip <= peakShoulder) && (peakShoulder <= peakWrist)

        return KineticChainResult(
            peakFrames = PeakFrames(hip = peakHip, shoulder = peakShoulder, wrist = peakWrist),
            timingMs = TimingMs(hipToShoulder = hipToShoulderMs, shoulderToWrist = shoulderToWristMs),
            isCorrectChain = isCorrectChain,
            velocities = JointVelocities(hip = velHip, shoulder = velShoulder, wrist = velWrist)
        )
    }
}
