package io.github.loje0611.tennisdoc.core.fusion.model

import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionDomainModelsTest {

    private fun fiveStages(): List<KineticStage> = listOf(
        KineticStage(KineticStageType.HIP, 1000L, 1f),
        KineticStage(KineticStageType.SHOULDER, 1040L, 2f, 40L),
        KineticStage(KineticStageType.WRIST, 1080L, 3f, 40L),
        KineticStage(KineticStageType.RACKET, 1110L, 4f, 30L),
        KineticStage(KineticStageType.IMPACT, 1130L, 5f, 20L),
    )

    @Test
    fun `kinetic stage types cover hip through impact`() {
        assertEquals(
            listOf(
                KineticStageType.HIP,
                KineticStageType.SHOULDER,
                KineticStageType.WRIST,
                KineticStageType.RACKET,
                KineticStageType.IMPACT,
            ),
            KineticStageType.entries,
        )
    }

    @Test
    fun `racket face states are open closed and square`() {
        assertEquals(
            listOf(RacketFaceState.OPEN, RacketFaceState.CLOSED, RacketFaceState.SQUARE),
            RacketFaceState.entries,
        )
    }

    @Test
    fun `racket impact orientation stores euler angles face and deviation`() {
        val orientation = RacketImpactOrientation(
            rollDeg = 12.5f,
            pitchDeg = -3.0f,
            yawDeg = 90.0f,
            faceState = RacketFaceState.OPEN,
            deviationDeg = 8.25f,
        )

        assertEquals(12.5f, orientation.rollDeg, 0.001f)
        assertEquals(-3.0f, orientation.pitchDeg, 0.001f)
        assertEquals(90.0f, orientation.yawDeg, 0.001f)
        assertEquals(RacketFaceState.OPEN, orientation.faceState)
        assertEquals(8.25f, orientation.deviationDeg, 0.001f)
    }

    @Test
    fun `imu data point stores accel and gyro axes`() {
        val sample = ImuDataPoint(
            timestampMs = 2500L,
            accelX = 0.1f,
            accelY = 9.81f,
            accelZ = -0.2f,
            gyroX = 1.5f,
            gyroY = -2.5f,
            gyroZ = 0.75f,
        )

        assertEquals(2500L, sample.timestampMs)
        assertEquals(0.1f, sample.accelX, 0.001f)
        assertEquals(9.81f, sample.accelY, 0.001f)
        assertEquals(-0.2f, sample.accelZ, 0.001f)
        assertEquals(1.5f, sample.gyroX, 0.001f)
        assertEquals(-2.5f, sample.gyroY, 0.001f)
        assertEquals(0.75f, sample.gyroZ, 0.001f)
    }

    @Test
    fun `fusion diagnosis stores tags cause feedback and explanation`() {
        val diagnosis = FusionDiagnosis(
            diagnosisTags = listOf("LATE_HIP", "OPEN_FACE"),
            primaryCause = "Hip rotation peaked after wrist",
            coachingFeedback = "Lead with the hip",
            causalExplanation = "Wrist peak preceded hip peak by 40ms.",
        )

        assertEquals(2, diagnosis.diagnosisTags.size)
        assertEquals("LATE_HIP", diagnosis.diagnosisTags[0])
        assertEquals("Hip rotation peaked after wrist", diagnosis.primaryCause)
        assertEquals("Lead with the hip", diagnosis.coachingFeedback)
        assertEquals("Wrist peak preceded hip peak by 40ms.", diagnosis.causalExplanation)
    }

    @Test
    fun `fused swing holds all domain contracts including optional diagnosis`() {
        val poses = listOf(PoseFrame(landmarks = listOf(PoseLandmark(0.4f, 0.5f, 0.1f))))
        val imu = listOf(ImuDataPoint(1000L, 0f, 1f, 0f, 0f, 0f, 0f))
        val swing = FusedSwing(
            swingId = "swing-1",
            sessionId = "session-1",
            drillType = DrillType.FOREHAND_FLAT,
            anchor = SyncAnchor(
                visionImpactTimestampMs = 1000L,
                sensorImpactTimestampMs = 1040L,
                confidence = 0.8f,
            ),
            kineticChain = KineticChain5Stage(
                stages = fiveStages(),
                isSequential = true,
                totalDurationMs = 130L,
                energyTransferEfficiency = 70f,
            ),
            racketImpact = RacketImpactOrientation(
                rollDeg = 0f,
                pitchDeg = 0f,
                yawDeg = 0f,
                faceState = RacketFaceState.CLOSED,
                deviationDeg = 15f,
            ),
            visionPoses = poses,
            imuSamples = imu,
        )

        assertEquals("swing-1", swing.swingId)
        assertEquals("session-1", swing.sessionId)
        assertEquals(DrillType.FOREHAND_FLAT, swing.drillType)
        assertEquals(40L, swing.anchor.timeOffsetMs)
        assertTrue(swing.anchor.isSynchronized)
        assertEquals(5, swing.kineticChain.stages.size)
        assertEquals(RacketFaceState.CLOSED, swing.racketImpact.faceState)
        assertEquals(1, swing.visionPoses.size)
        assertEquals(1, swing.imuSamples.size)
        assertNull(swing.diagnosis)
    }

    @Test
    fun `zero offset can still be marked unsynchronized when caller overrides flag`() {
        val anchor = SyncAnchor(
            visionImpactTimestampMs = 0L,
            sensorImpactTimestampMs = 0L,
            confidence = 0f,
            isSynchronized = false,
        )

        assertEquals(0L, anchor.timeOffsetMs)
        assertEquals(0f, anchor.confidence, 0.001f)
        assertFalse(anchor.isSynchronized)
    }
}
