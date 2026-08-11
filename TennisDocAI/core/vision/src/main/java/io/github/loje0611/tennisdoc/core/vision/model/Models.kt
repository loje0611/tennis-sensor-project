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
