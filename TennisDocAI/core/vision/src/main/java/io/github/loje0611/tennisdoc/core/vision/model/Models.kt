package io.github.loje0611.tennisdoc.core.vision.model

data class PoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float = 1.0f
) {
    val isNan: Boolean get() = x.isNaN() || y.isNaN() || z.isNaN()
}

data class PoseFrame(
    val landmarks: List<PoseLandmark>
)

data class JointAngleResult(
    val rightArmAngle: Double,
    val rightKneeAngle: Double
)

data class PeakFrames(
    val hip: Int,
    val shoulder: Int,
    val wrist: Int
)

data class TimingMs(
    val hipToShoulder: Double,
    val shoulderToWrist: Double
)

data class JointVelocities(
    val hip: List<Float>,
    val shoulder: List<Float>,
    val wrist: List<Float>
)

data class KineticChainResult(
    val peakFrames: PeakFrames,
    val timingMs: TimingMs,
    val isCorrectChain: Boolean,
    val velocities: JointVelocities
)
