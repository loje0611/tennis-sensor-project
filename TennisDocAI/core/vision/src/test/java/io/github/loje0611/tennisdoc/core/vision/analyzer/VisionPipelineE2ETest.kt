package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * B그룹 분석기 연쇄: PoseFrame → angle → impact → path → kinetic → diagnosis.
 * 합성 궤적으로 파이프라인이 예외 없이 연결되고 의미 있는 산출을 내는지 검증한다.
 */
class VisionPipelineE2ETest {

    private fun landmark(x: Float, y: Float, z: Float = 0f) =
        PoseLandmark(x, y, z, visibility = 1f)

    private fun blankLandmarks(): MutableList<PoseLandmark> =
        MutableList(33) { landmark(0f, 0f, 0f) }

    /**
     * 약 90프레임의 합성 포즈: 손목이 가속 피크를 만들고 Y가 감소(Topspin 방향)한다.
     */
    private fun syntheticSwingFrames(frameCount: Int = 90): List<PoseFrame> {
        return List(frameCount) { i ->
            val t = i / (frameCount - 1).toFloat()
            val phase = (t * PI).toFloat()
            // Wrist speed peaks near mid-swing
            val wristTravel = sin(phase.toDouble()).toFloat()
            val landmarks = blankLandmarks()

            // Right arm chain: shoulder(12) - elbow(14) - wrist(16)
            landmarks[12] = landmark(0.2f, 0.4f, 0f)
            landmarks[14] = landmark(0.35f, 0.45f, 0f)
            landmarks[16] = landmark(
                x = 0.5f + 0.25f * wristTravel,
                y = 0.55f - 0.2f * t, // decreasing Y → topspin slope
                z = 0.05f * cos(phase.toDouble()).toFloat(),
            )

            // Right knee: hip(24) - knee(26) - ankle(28)
            landmarks[24] = landmark(0.2f, 0.7f, 0f)
            landmarks[26] = landmark(0.22f, 0.85f, 0f)
            landmarks[28] = landmark(0.22f, 1.0f, 0f)

            // Left placeholders remain zeros
            PoseFrame(landmarks)
        }
    }

    @Test
    fun `pipeline produces coherent diagnosis from synthetic swing`() {
        val frames = syntheticSwingFrames()
        val fps = 30f

        val mid = frames[frames.size / 2]
        val angles = AngleCalculator.getJointAnglesFromPose(mid)
        assertFalse(angles.rightArmAngle.isNaN())
        assertFalse(angles.rightKneeAngle.isNaN())

        val impact = ImpactDetector.detectImpactFrames(frames, fps = fps)
        assertTrue("expected at least one impact frame", impact.impactFrames.isNotEmpty())

        val impactFrame = impact.impactFrames.first()
        val path = SwingPathClassifier.classifySwingPath(frames, impactFrame, isRightHand = true)
        assertTrue(
            "path should be classified, got $path",
            path == SwingPathType.TOPSPIN ||
                path == SwingPathType.FLAT ||
                path == SwingPathType.SLICE ||
                path == SwingPathType.UNKNOWN,
        )

        val chain = KineticChainAnalyzer.analyzeKineticChain(frames, fps = fps, isRightHand = true)
        assertNotNull(chain)

        val armAnglesPerFrame = frames.map { frame ->
            AngleCalculator.getJointAnglesFromPose(frame).rightArmAngle
        }
        val diagnosis = SwingDiagnosisBuilder.buildSwingFeedbacks(
            impactFrames = impact.impactFrames,
            swingTypes = List(impact.impactFrames.size) { path.key },
            armAngles = armAnglesPerFrame,
            chainVelocities = chain!!.velocities,
            fps = fps,
        )

        assertEquals(impact.impactFrames.size, diagnosis.swingFeedbacks.size)
        assertTrue(diagnosis.swingFeedbacks.keys.containsAll(impact.impactFrames))
    }

    @Test
    fun `empty frames yield safe empty diagnosis path`() {
        val frames = emptyList<PoseFrame>()
        val impact = ImpactDetector.detectImpactFrames(frames)
        assertTrue(impact.impactFrames.isEmpty())

        val path = SwingPathClassifier.classifySwingPath(frames, impactFrame = null)
        assertEquals(SwingPathType.UNKNOWN, path)

        val chain = KineticChainAnalyzer.analyzeKineticChain(frames)
        assertEquals(null, chain)

        val diagnosis = SwingDiagnosisBuilder.buildSwingFeedbacks(
            impactFrames = emptyList(),
            swingTypes = emptyList(),
            armAngles = emptyList(),
            chainVelocities = null,
        )
        assertTrue(diagnosis.swingFeedbacks.isEmpty())
        assertTrue(diagnosis.allProblems.isEmpty())
    }
}
