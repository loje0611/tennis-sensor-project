package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.JointAngleResult
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import kotlin.math.acos
import kotlin.math.sqrt

object AngleCalculator {

    /**
     * Calculates the 3D angle (in degrees) between three points: a, b, c, where b is the center point.
     * Returns Double.NaN if any of the points are NaN or if either of the vectors has zero length.
     */
    fun calculate3dAngle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Double {
        if (a.isNan || b.isNan || c.isNan) {
            return Double.NaN
        }

        val baX = a.x - b.x
        val baY = a.y - b.y
        val baZ = a.z - b.z

        val bcX = c.x - b.x
        val bcY = c.y - b.y
        val bcZ = c.z - b.z

        val lenBA = sqrt(baX * baX + baY * baY + baZ * baZ)
        val lenBC = sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)

        if (lenBA == 0.0f || lenBC == 0.0f) {
            return Double.NaN
        }

        val dot = baX * bcX + baY * bcY + baZ * bcZ
        var cosVal = dot / (lenBA * lenBC)

        // Clip the cosine value to the [-1.0, 1.0] range to avoid floating-point errors for acos
        cosVal = cosVal.coerceIn(-1.0f, 1.0f)

        val angleRad = acos(cosVal)
        return Math.toDegrees(angleRad.toDouble())
    }

    /**
     * Extracts joint angles from a PoseFrame for the right arm and right knee.
     */
    fun getJointAnglesFromPose(poseFrame: PoseFrame): JointAngleResult {
        val landmarks = poseFrame.landmarks
        if (landmarks.size < 33) {
            return JointAngleResult(Double.NaN, Double.NaN)
        }

        val rightArmAngle = calculate3dAngle(landmarks[12], landmarks[14], landmarks[16])
        val rightKneeAngle = calculate3dAngle(landmarks[24], landmarks[26], landmarks[28])

        return JointAngleResult(rightArmAngle, rightKneeAngle)
    }
}
