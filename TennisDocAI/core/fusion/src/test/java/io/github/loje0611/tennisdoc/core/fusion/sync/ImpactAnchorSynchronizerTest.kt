package io.github.loje0611.tennisdoc.core.fusion.sync

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpactAnchorSynchronizerTest {

    private val synchronizer = ImpactAnchorSynchronizer()

    /**
     * Helper to create 60 frames (2 seconds @ 30fps) with wrist speed spike at [peakFrameIndex].
     */
    private fun createPoseSequence(peakFrameIndex: Int = 30): List<PoseFrame> {
        val totalFrames = 60
        return (0 until totalFrames).map { frameIdx ->
            val landmarks = (0..32).map { jointIdx ->
                if (jointIdx == 16) { // Right wrist
                    val x = if (frameIdx < peakFrameIndex) {
                        0.2f + frameIdx * 0.005f
                    } else if (frameIdx == peakFrameIndex) {
                        0.8f // sudden jump
                    } else {
                        0.85f + (frameIdx - peakFrameIndex) * 0.002f
                    }
                    PoseLandmark(x, 0.5f, 0.0f, 1.0f)
                } else {
                    PoseLandmark(0.5f, 0.5f, 0.0f, 1.0f)
                }
            }
            PoseFrame(landmarks)
        }
    }

    /**
     * Helper to create IMU samples (50Hz) with peak acceleration spike at [peakTimestampMs].
     */
    private fun createImuSequence(
        startTimestampMs: Long = 0L,
        durationMs: Long = 2000L,
        peakTimestampMs: Long = 1000L,
        peakAccelG: Float = 8.5f
    ): List<ImuDataPoint> {
        val stepMs = 10L
        val samples = mutableListOf<ImuDataPoint>()
        var ts = startTimestampMs
        while (ts <= startTimestampMs + durationMs) {
            val isPeak = ts == peakTimestampMs
            val accelMag = if (isPeak) peakAccelG else 1.0f
            samples.add(
                ImuDataPoint(
                    timestampMs = ts,
                    accelX = accelMag,
                    accelY = 0f,
                    accelZ = 0f,
                    gyroX = if (isPeak) 1200f else 50f,
                    gyroY = 0f,
                    gyroZ = 0f
                )
            )
            ts += stepMs
        }
        return samples
    }

    @Test
    fun `AC-2 exact sync with 0ms offset yields synchronized anchor and high confidence`() {
        // Frame 30 @ 30fps is 1000ms.
        val poses = createPoseSequence(peakFrameIndex = 30)
        val imu = createImuSequence(peakTimestampMs = 1000L, peakAccelG = 8.0f)

        val anchor = synchronizer.synchronize(
            poses = poses,
            imuSamples = imu,
            baseVisionTimestampMs = 0L,
            visionFps = 30f
        )

        assertEquals(1000L, anchor.visionImpactTimestampMs)
        assertEquals(1000L, anchor.sensorImpactTimestampMs)
        assertEquals(0L, anchor.timeOffsetMs)
        assertTrue(anchor.isSynchronized)
        assertTrue("Confidence should be >= 0.8f, got ${anchor.confidence}", anchor.confidence >= 0.8f)
    }

    @Test
    fun `AC-3 sensor lagging by 30ms yields exact positive offset and is synchronized`() {
        val poses = createPoseSequence(peakFrameIndex = 30) // 1000ms
        val imu = createImuSequence(peakTimestampMs = 1030L, peakAccelG = 8.0f)

        val anchor = synchronizer.synchronize(
            poses = poses,
            imuSamples = imu,
            baseVisionTimestampMs = 0L,
            visionFps = 30f
        )

        assertEquals(1000L, anchor.visionImpactTimestampMs)
        assertEquals(1030L, anchor.sensorImpactTimestampMs)
        assertEquals(30L, anchor.timeOffsetMs)
        assertTrue(anchor.isSynchronized)
        assertTrue(anchor.confidence > 0.6f)
    }

    @Test
    fun `AC-4 time offset exceeding 150ms window results in unsynchronized and zero confidence`() {
        val poses = createPoseSequence(peakFrameIndex = 30) // 1000ms
        val imu = createImuSequence(peakTimestampMs = 1250L, peakAccelG = 9.0f) // 250ms offset

        val anchor = synchronizer.synchronize(
            poses = poses,
            imuSamples = imu,
            baseVisionTimestampMs = 0L,
            visionFps = 30f
        )

        assertEquals(1000L, anchor.visionImpactTimestampMs)
        assertEquals(1250L, anchor.sensorImpactTimestampMs)
        assertEquals(250L, anchor.timeOffsetMs)
        assertFalse(anchor.isSynchronized)
        assertEquals(0.0f, anchor.confidence, 0.001f)
    }

    @Test
    fun `AC-5 empty inputs or weak sensor impact gracefully returns unsynchronized anchor`() {
        val emptyPosesAnchor = synchronizer.synchronize(
            poses = emptyList(),
            imuSamples = createImuSequence()
        )
        assertFalse(emptyPosesAnchor.isSynchronized)
        assertEquals(0.0f, emptyPosesAnchor.confidence, 0.001f)

        val emptyImuAnchor = synchronizer.synchronize(
            poses = createPoseSequence(),
            imuSamples = emptyList()
        )
        assertFalse(emptyImuAnchor.isSynchronized)
        assertEquals(0.0f, emptyImuAnchor.confidence, 0.001f)

        // Weak swing with peak accel < 4.0G
        val weakImu = createImuSequence(peakAccelG = 2.5f)
        val weakAnchor = synchronizer.synchronize(
            poses = createPoseSequence(),
            imuSamples = weakImu
        )
        assertFalse(weakAnchor.isSynchronized)
        assertEquals(0.0f, weakAnchor.confidence, 0.001f)
    }

    @Test
    fun `AC-6 negative offset within 100ms is synchronized`() {
        // Sensor peaks earlier than vision (e.g. sensor at 960ms, vision at 1000ms -> offset = -40ms)
        val poses = createPoseSequence(peakFrameIndex = 30) // 1000ms
        val imu = createImuSequence(peakTimestampMs = 960L, peakAccelG = 7.5f)

        val anchor = synchronizer.synchronize(
            poses = poses,
            imuSamples = imu,
            baseVisionTimestampMs = 0L,
            visionFps = 30f
        )

        assertEquals(1000L, anchor.visionImpactTimestampMs)
        assertEquals(960L, anchor.sensorImpactTimestampMs)
        assertEquals(-40L, anchor.timeOffsetMs)
        assertTrue(anchor.isSynchronized)
        assertTrue(anchor.confidence > 0.6f)
    }
}
