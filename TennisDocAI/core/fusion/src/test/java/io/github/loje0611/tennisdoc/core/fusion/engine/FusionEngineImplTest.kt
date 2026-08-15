package io.github.loje0611.tennisdoc.core.fusion.engine

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionEngineImplTest {

    private val fusionEngine = FusionEngineImpl()

    private fun createPoseSequence(): List<PoseFrame> {
        return (0..60).map { frameIdx ->
            val landmarks = (0..32).map { jointIdx ->
                val x = when (jointIdx) {
                    24 -> if (frameIdx == 3) 0.8f else 0.2f // Hip peak
                    12 -> if (frameIdx == 4) 0.8f else 0.2f // Shoulder peak
                    16 -> if (frameIdx == 5) 0.9f else 0.2f // Wrist peak
                    else -> 0.5f
                }
                PoseLandmark(x, 0.5f, 0.0f, 1.0f)
            }
            PoseFrame(landmarks)
        }
    }

    private fun createImuSequence(): List<ImuDataPoint> {
        val samples = mutableListOf<ImuDataPoint>()
        var ts = 0L
        while (ts <= 1000L) {
            val gyro = if (ts == 210L) 1800f else 100f
            val accel = if (ts == 230L) 25f else 1f
            samples.add(
                ImuDataPoint(
                    timestampMs = ts,
                    accelX = accel,
                    accelY = 0f,
                    accelZ = 0f,
                    gyroX = 0f,
                    gyroY = 0f,
                    gyroZ = gyro
                )
            )
            ts += 10L
        }
        return samples
    }

    @Test
    fun `AC-5 full pipeline fuse creates complete FusedSwing container`() {
        val poses = createPoseSequence()
        val imu = createImuSequence()

        val fusedSwing = fusionEngine.fuse(
            DrillType.FOREHAND,
            poses,
            imu
        )

        assertNotNull(fusedSwing.swingId)
        assertTrue(fusedSwing.sessionId.startsWith("fusion-session-"))
        assertEquals(DrillType.FOREHAND, fusedSwing.drillType)
        assertNotNull(fusedSwing.anchor)
        assertEquals(5, fusedSwing.kineticChain.stages.size)
        assertNotNull(fusedSwing.racketImpact)
        assertEquals(poses.size, fusedSwing.visionPoses.size)
        assertEquals(imu.size, fusedSwing.imuSamples.size)
        assertNotNull(fusedSwing.diagnosis)
    }

    @Test
    fun `empty inputs do not crash and still return a five-stage FusedSwing`() {
        val fusedSwing = fusionEngine.fuse(
            DrillType.SERVE,
            emptyList(),
            emptyList(),
        )

        assertEquals(DrillType.SERVE, fusedSwing.drillType)
        assertEquals(5, fusedSwing.kineticChain.stages.size)
        assertNotNull(fusedSwing.diagnosis)
        assertTrue(fusedSwing.diagnosis!!.diagnosisTags.contains("SYNC_FAILED"))
    }
}
