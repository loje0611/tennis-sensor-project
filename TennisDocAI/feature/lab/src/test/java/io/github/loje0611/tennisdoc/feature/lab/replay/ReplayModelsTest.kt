package io.github.loje0611.tennisdoc.feature.lab.replay

import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.vision.analyzer.SwingPathType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayModelsTest {

    @Test
    fun racketFaceStateLabel_mapsSquareOpenClosedToKoreanCopy() {
        assertEquals("🟢 정타 (스퀘어)", racketFaceStateLabel(RacketFaceState.SQUARE))
        assertEquals("🟠 페이스 열림 (공이 뜨는 원인)", racketFaceStateLabel(RacketFaceState.OPEN))
        assertEquals("🔵 페이스 닫힘 (네트에 걸리는 원인)", racketFaceStateLabel(RacketFaceState.CLOSED))
    }

    @Test
    fun swingPathTypeDisplayLabel_mapsClassifierTypes() {
        assertEquals("📈 탑스핀 (상향 스윙 궤적)", swingPathTypeDisplayLabel(SwingPathType.TOPSPIN))
        assertEquals("⚡ 플랫 (수평 스윙 궤적)", swingPathTypeDisplayLabel(SwingPathType.FLAT))
        assertEquals("📉 슬라이스 (하향 스윙 궤적)", swingPathTypeDisplayLabel(SwingPathType.SLICE))
        assertEquals("알 수 없음", swingPathTypeDisplayLabel(SwingPathType.UNKNOWN))
    }

    @Test
    fun isExistingVideoFile_trueOnlyWhenPathPointsAtRealFile() {
        val missing = File(System.getProperty("java.io.tmpdir"), "missing-swing-${System.nanoTime()}.mp4")
        assertFalse(isExistingVideoFile(null))
        assertFalse(isExistingVideoFile(""))
        assertFalse(isExistingVideoFile("   "))
        assertFalse(isExistingVideoFile(missing.absolutePath))

        val present = File.createTempFile("swing-clip", ".mp4")
        try {
            present.writeBytes(byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70))
            assertTrue(isExistingVideoFile(present.absolutePath))
        } finally {
            present.delete()
        }
    }

    @Test
    fun buildSwingTrailPoints_mapsRightWristAndNormalizesProgress() {
        val frames = (0..2).map { index ->
            val landmarks = MutableList(33) {
                PoseLandmark(x = 0.1f, y = 0.1f, z = 0f, visibility = 0.9f)
            }
            landmarks[16] = PoseLandmark(
                x = 0.2f + index * 0.2f,
                y = 0.3f + index * 0.1f,
                z = 0f,
                visibility = 0.9f,
            )
            PoseFrame(landmarks = landmarks)
        }

        val points = buildSwingTrailPoints(frames, isRightHand = true)
        assertEquals(3, points.size)
        assertEquals(0.2f, points[0].x, 0.0001f)
        assertEquals(0.3f, points[0].y, 0.0001f)
        assertEquals(0f, points[0].progress, 0.0001f)
        assertEquals(0.6f, points[2].x, 0.0001f)
        assertEquals(0.5f, points[2].y, 0.0001f)
        assertEquals(1f, points[2].progress, 0.0001f)
        assertEquals(0.5f, points[1].progress, 0.0001f)
    }

    @Test
    fun buildSwingTrailPoints_emptyPosesYieldEmptyTrail() {
        assertTrue(buildSwingTrailPoints(emptyList()).isEmpty())
    }
}
