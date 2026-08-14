package io.github.loje0611.tennisdoc.feature.lab.pipeline

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabFusionStreamBufferTest {

    private fun createPose(): PoseFrame {
        return PoseFrame(landmarks = (0..32).map { PoseLandmark(0.5f, 0.5f, 0f, 1f) })
    }

    @Test
    fun `AC-2 ring buffer maintains only samples within 3000ms duration`() {
        val buffer = LabFusionStreamBuffer(bufferDurationMs = 3000L)

        // Add IMU samples from t=0 to t=4000ms (every 100ms)
        for (t in 0..4000 step 100) {
            buffer.addImuSample(
                ImuDataPoint(
                    timestampMs = t.toLong(),
                    accelX = 1f,
                    accelY = 0f,
                    accelZ = 0f,
                    gyroX = 0f,
                    gyroY = 0f,
                    gyroZ = 0f
                )
            )
        }

        val (_, imuSnapshot) = buffer.snapshot()

        // Threshold = 4000 - 3000 = 1000ms. Samples with ts in 1000..4000 (31 samples)
        assertEquals(31, imuSnapshot.size)
        assertEquals(1000L, imuSnapshot.first().timestampMs)
        assertEquals(4000L, imuSnapshot.last().timestampMs)
    }

    @Test
    fun `clear empties both pose and imu buffers`() {
        val buffer = LabFusionStreamBuffer(bufferDurationMs = 3000L)
        buffer.addPoseFrame(createPose())
        buffer.addImuSample(ImuDataPoint(100L, 0f, 0f, 0f, 0f, 0f, 0f))

        var snapshot = buffer.snapshot()
        assertEquals(1, snapshot.first.size)
        assertEquals(1, snapshot.second.size)

        buffer.clear()
        snapshot = buffer.snapshot()
        assertTrue(snapshot.first.isEmpty())
        assertTrue(snapshot.second.isEmpty())
    }
}
