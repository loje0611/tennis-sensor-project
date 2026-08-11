package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B그룹 골든 픽스처를 보완하는 경계·실패모드 단위 테스트.
 */
class VisionEdgeCasesTest {

    private fun lm(x: Float, y: Float, z: Float = 0f) = PoseLandmark(x, y, z)

    @Test
    fun `angle with NaN landmark returns NaN`() {
        val a = lm(1f, 0f, 0f)
        val b = lm(Float.NaN, 0f, 0f)
        val c = lm(0f, 1f, 0f)
        val angle = AngleCalculator.calculate3dAngle(a, b, c)
        assertTrue(angle.isNaN())
    }

    @Test
    fun `velocity for single frame is empty`() {
        val frame = PoseFrame(List(33) { lm(0f, 0f) })
        val vel = ImpactDetector.calculateVelocity(listOf(frame), jointIndex = 16)
        assertTrue(vel.isEmpty())
    }

    @Test
    fun `swing path unknown when impact is null`() {
        val frames = List(5) { PoseFrame(List(33) { lm(0f, it.toFloat() * 0.01f) }) }
        assertEquals(SwingPathType.UNKNOWN, SwingPathClassifier.classifySwingPath(frames, null))
    }

    @Test
    fun `swing path unknown when trajectory shorter than two points`() {
        val landmarks = List(33) { lm(0f, 0f) }
        val frames = listOf(PoseFrame(landmarks), PoseFrame(landmarks))
        // impact at 0 with tiny window still may gather points; force empty by NaN wrists
        val nanWrist = landmarks.toMutableList().also {
            it[16] = PoseLandmark(Float.NaN, Float.NaN, Float.NaN)
        }
        val nanFrames = List(5) { PoseFrame(nanWrist) }
        assertEquals(
            SwingPathType.UNKNOWN,
            SwingPathClassifier.classifySwingPath(nanFrames, impactFrame = 2),
        )
    }

    @Test
    fun `kinetic chain null when fewer than two frames`() {
        assertNull(KineticChainAnalyzer.analyzeKineticChain(emptyList()))
        assertNull(
            KineticChainAnalyzer.analyzeKineticChain(
                listOf(PoseFrame(List(33) { lm(0f, 0f) })),
            ),
        )
    }

    @Test
    fun `diagnosis with empty impacts returns empty map`() {
        val result = SwingDiagnosisBuilder.buildSwingFeedbacks(
            impactFrames = emptyList(),
            swingTypes = emptyList(),
            armAngles = emptyList(),
            chainVelocities = null,
        )
        assertTrue(result.swingFeedbacks.isEmpty())
        assertTrue(result.allProblems.isEmpty())
    }

    @Test
    fun `linear slope NaN for single sample`() {
        assertTrue(SwingPathClassifier.calculateLinearSlope(doubleArrayOf(1.0)).isNaN())
    }
}
