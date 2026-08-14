package io.github.loje0611.tennisdoc.core.fusion.analysis

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KineticChain5StageAnalyzerTest {

    private val analyzer = KineticChain5StageAnalyzer(visionFps = 30f, isRightHand = true)

    /**
     * Creates a 60-frame pose list where Hip peaks at [hipPeakFrame], Shoulder at [shoulderPeakFrame], Wrist at [wristPeakFrame].
     */
    private fun createPoseSequence(
        hipPeakFrame: Int,
        shoulderPeakFrame: Int,
        wristPeakFrame: Int
    ): List<PoseFrame> {
        val totalFrames = 60
        return (0 until totalFrames).map { frameIdx ->
            val landmarks = (0..32).map { jointIdx ->
                val x = when (jointIdx) {
                    24 -> if (frameIdx == hipPeakFrame) 0.8f else 0.2f // Right Hip
                    12 -> if (frameIdx == shoulderPeakFrame) 0.8f else 0.2f // Right Shoulder
                    16 -> if (frameIdx == wristPeakFrame) 0.9f else 0.2f // Right Wrist
                    else -> 0.5f
                }
                PoseLandmark(x, 0.5f, 0.0f, 1.0f)
            }
            PoseFrame(landmarks)
        }
    }

    private fun createImuSequence(
        racketPeakTs: Long,
        impactPeakTs: Long,
        startTs: Long = 0L,
        durationMs: Long = 1000L
    ): List<ImuDataPoint> {
        val samples = mutableListOf<ImuDataPoint>()
        var ts = startTs
        while (ts <= startTs + durationMs) {
            val gyro = if (ts == racketPeakTs) 1800f else 100f
            val accel = if (ts == impactPeakTs) 25f else 1f
            samples.add(
                ImuDataPoint(
                    timestampMs = ts,
                    accelX = accel,
                    accelY = 0f,
                    accelZ = 0f,
                    gyroX = gyro,
                    gyroY = 0f,
                    gyroZ = 0f
                )
            )
            ts += 10L
        }
        return samples
    }

    @Test
    fun `AC-2 perfect sequential chain yields high efficiency and isSequential true`() {
        // Frame 3 = 100ms, Frame 4 = 133ms (~140ms), Frame 5 = 166ms (~180ms)
        // Let's set wristPeakFrame = 5 (166ms)
        val poses = createPoseSequence(hipPeakFrame = 3, shoulderPeakFrame = 4, wristPeakFrame = 5)
        // Sensor: Racket peak at 210ms, Impact peak at 230ms
        val imu = createImuSequence(racketPeakTs = 210L, impactPeakTs = 230L)
        val anchor = SyncAnchor(
            visionImpactTimestampMs = 166L,
            sensorImpactTimestampMs = 230L,
            timeOffsetMs = 0L,
            confidence = 0.95f,
            isSynchronized = true
        )

        val result = analyzer.analyze(poses, imu, anchor)

        assertEquals(5, result.stages.size)
        assertEquals(KineticStageType.HIP, result.stages[0].stage)
        assertEquals(KineticStageType.SHOULDER, result.stages[1].stage)
        assertEquals(KineticStageType.WRIST, result.stages[2].stage)
        assertEquals(KineticStageType.RACKET, result.stages[3].stage)
        assertEquals(KineticStageType.IMPACT, result.stages[4].stage)

        assertTrue(result.isSequential)
        assertTrue("Expected efficiency >= 90.0f, got ${result.energyTransferEfficiency}", result.energyTransferEfficiency >= 90.0f)
        assertTrue(result.totalDurationMs > 0L)
    }

    @Test
    fun `AC-3 broken chain with early shoulder rotation yields isSequential false and low efficiency`() {
        // Shoulder peaks before Hip (Frame 3 shoulder, Frame 5 hip, Frame 6 wrist)
        val poses = createPoseSequence(hipPeakFrame = 5, shoulderPeakFrame = 3, wristPeakFrame = 6)
        val imu = createImuSequence(racketPeakTs = 250L, impactPeakTs = 280L)
        val anchor = SyncAnchor(
            visionImpactTimestampMs = 200L,
            sensorImpactTimestampMs = 280L,
            timeOffsetMs = 0L,
            confidence = 0.85f,
            isSynchronized = true
        )

        val result = analyzer.analyze(poses, imu, anchor)

        assertFalse(result.isSequential)
        assertTrue("Expected efficiency < 50.0f, got ${result.energyTransferEfficiency}", result.energyTransferEfficiency < 50.0f)
    }

    @Test
    fun `AC-4 timeOffsetMs is properly applied to align sensor timestamps to vision time axis`() {
        val poses = createPoseSequence(hipPeakFrame = 3, shoulderPeakFrame = 4, wristPeakFrame = 5)
        // Sensor timestamps have +50ms offset (e.g. racket at 260ms, impact at 280ms)
        val imu = createImuSequence(racketPeakTs = 260L, impactPeakTs = 280L)
        val anchor = SyncAnchor(
            visionImpactTimestampMs = 166L,
            sensorImpactTimestampMs = 280L,
            timeOffsetMs = 50L,
            confidence = 0.90f,
            isSynchronized = true
        )

        val result = analyzer.analyze(poses, imu, anchor)

        // Aligned racket = 260 - 50 = 210ms
        // Aligned impact = 280 - 50 = 230ms
        assertEquals(210L, result.stages[3].peakTimestampMs)
        assertEquals(230L, result.stages[4].peakTimestampMs)
        assertTrue(result.isSequential)
    }

    @Test
    fun `AC-5 empty or invalid input produces safe fallback 5 stages`() {
        val anchor = SyncAnchor(0L, 0L, 0L, 0f, false)
        val emptyPosesResult = analyzer.analyze(emptyList(), createImuSequence(200L, 250L), anchor)
        assertEquals(5, emptyPosesResult.stages.size)
        assertFalse(emptyPosesResult.isSequential)
        assertEquals(0.0f, emptyPosesResult.energyTransferEfficiency, 0.001f)

        val emptyImuResult = analyzer.analyze(createPoseSequence(3, 4, 5), emptyList(), anchor)
        assertEquals(5, emptyImuResult.stages.size)
        assertFalse(emptyImuResult.isSequential)
        assertEquals(0.0f, emptyImuResult.energyTransferEfficiency, 0.001f)
    }

    @Test
    fun `FR-5 unsynchronized anchor produces safe fallback even with sequential inputs`() {
        val poses = createPoseSequence(hipPeakFrame = 3, shoulderPeakFrame = 4, wristPeakFrame = 5)
        val imu = createImuSequence(racketPeakTs = 210L, impactPeakTs = 230L)
        val unsyncAnchor = SyncAnchor(
            visionImpactTimestampMs = 166L,
            sensorImpactTimestampMs = 230L,
            timeOffsetMs = 0L,
            confidence = 0.0f,
            isSynchronized = false
        )

        val result = analyzer.analyze(poses, imu, unsyncAnchor)
        assertEquals(5, result.stages.size)
        assertFalse(result.isSequential)
        assertEquals(0.0f, result.energyTransferEfficiency, 0.001f)
    }
}
