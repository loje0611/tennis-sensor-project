package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark

enum class SwingPathType(val key: String) {
    TOPSPIN("Topspin"),
    FLAT("Flat"),
    SLICE("Slice"),
    UNKNOWN("Unknown")
}

object SwingPathClassifier {

    fun calculateLinearSlope(yValues: DoubleArray): Double {
        val n = yValues.size
        if (n < 2) return Double.NaN

        var sumX = 0.0
        var sumX2 = 0.0
        var sumY = 0.0
        var sumXY = 0.0

        for (i in 0 until n) {
            val x = i.toDouble()
            val y = yValues[i]
            sumX += x
            sumX2 += x * x
            sumY += y
            sumXY += x * y
        }

        val denom = n * sumX2 - sumX * sumX
        if (denom == 0.0) return Double.NaN

        return (n * sumXY - sumX * sumY) / denom
    }

    fun classifySwingPath(
        poseFrames: List<PoseFrame>,
        impactFrame: Int?,
        isRightHand: Boolean = true,
        analysisWindow: Int = 10
    ): SwingPathType {
        if (impactFrame == null || poseFrames.isEmpty()) {
            return SwingPathType.UNKNOWN
        }

        val wristIndex = if (isRightHand) 16 else 15

        val startFrame = maxOf(0, impactFrame - analysisWindow)
        val endFrame = minOf(poseFrames.size, impactFrame + analysisWindow)

        val yTrajectory = mutableListOf<Double>()
        for (i in startFrame until endFrame) {
            val landmark = poseFrames[i].landmarks.getOrNull(wristIndex)
            if (landmark != null && !landmark.isNan) {
                yTrajectory.add(landmark.y.toDouble())
            }
        }

        if (yTrajectory.size < 2) {
            return SwingPathType.UNKNOWN
        }

        val slope = calculateLinearSlope(yTrajectory.toDoubleArray())
        if (slope.isNaN()) {
            return SwingPathType.UNKNOWN
        }

        val threshold = 0.005
        return when {
            slope < -threshold -> SwingPathType.TOPSPIN
            slope > threshold -> SwingPathType.SLICE
            else -> SwingPathType.FLAT
        }
    }

    fun getWristTrajectory3d(
        poseFrames: List<PoseFrame>,
        isRightHand: Boolean = true
    ): List<PoseLandmark> {
        val wristIndex = if (isRightHand) 16 else 15
        val trajectory = mutableListOf<PoseLandmark>()
        
        for (frame in poseFrames) {
            val landmark = frame.landmarks.getOrNull(wristIndex)
            if (landmark != null && !landmark.isNan) {
                trajectory.add(landmark)
            }
        }
        
        return trajectory
    }
}
