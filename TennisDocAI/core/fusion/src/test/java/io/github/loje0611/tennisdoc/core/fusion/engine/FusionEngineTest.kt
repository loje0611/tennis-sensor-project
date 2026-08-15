package io.github.loje0611.tennisdoc.core.fusion.engine

import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.FusionDiagnosis
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FusionEngineTest {

    private class StubFusionEngine : FusionEngine {
        override fun fuse(
            drillType: DrillType,
            poses: List<PoseFrame>,
            imuSamples: List<ImuDataPoint>
        ): FusedSwing {
            val empty = poses.isEmpty() || imuSamples.isEmpty()
            val stages = listOf(
                KineticStage(KineticStageType.HIP, 1000L, 450f),
                KineticStage(KineticStageType.SHOULDER, 1050L, 600f, 50L),
                KineticStage(KineticStageType.WRIST, 1090L, 950f, 40L),
                KineticStage(KineticStageType.RACKET, 1120L, 1600f, 30L),
                KineticStage(KineticStageType.IMPACT, 1140L, 22f, 20L)
            )
            return FusedSwing(
                swingId = UUID.randomUUID().toString(),
                sessionId = "test-session-1",
                drillType = drillType,
                anchor = if (empty) {
                    SyncAnchor(
                        visionImpactTimestampMs = 0L,
                        sensorImpactTimestampMs = 0L,
                        confidence = 0f,
                        isSynchronized = false,
                    )
                } else {
                    SyncAnchor(
                        visionImpactTimestampMs = 1130L,
                        sensorImpactTimestampMs = 1140L,
                        confidence = 0.92f
                    )
                },
                kineticChain = KineticChain5Stage(
                    stages = stages,
                    isSequential = true,
                    totalDurationMs = 140L,
                    energyTransferEfficiency = 91.2f
                ),
                racketImpact = RacketImpactOrientation(
                    rollDeg = 2.5f,
                    pitchDeg = 1.0f,
                    yawDeg = 88.0f,
                    faceState = RacketFaceState.SQUARE,
                    deviationDeg = 2.5f
                ),
                visionPoses = poses,
                imuSamples = imuSamples,
                diagnosis = FusionDiagnosis(
                    diagnosisTags = listOf("CLEAN_IMPACT", "OPTIMAL_CHAIN"),
                    primaryCause = "Well-timed kinetic transfer",
                    coachingFeedback = "Great hip-lead topspin drive",
                    causalExplanation = "Hip acceleration initiated 90ms before wrist peak, maximizing racket lag."
                )
            )
        }
    }

    @Test
    fun `stub FusionEngine produces complete FusedSwing container`() {
        val engine: FusionEngine = StubFusionEngine()

        val dummyPoses = listOf(
            PoseFrame(
                landmarks = listOf(
                    PoseLandmark(0.5f, 0.5f, 0.0f, 1.0f)
                )
            )
        )
        val dummyImu = listOf(
            ImuDataPoint(
                timestampMs = 1000L,
                accelX = 0.1f, accelY = 9.8f, accelZ = 0.2f,
                gyroX = 10f, gyroY = 5f, gyroZ = 2f
            )
        )

        val result = engine.fuse(
            drillType = DrillType.FOREHAND,
            poses = dummyPoses,
            imuSamples = dummyImu
        )

        assertNotNull(result.swingId)
        assertEquals("test-session-1", result.sessionId)
        assertEquals(DrillType.FOREHAND, result.drillType)
        assertTrue(result.anchor.isSynchronized)
        assertEquals(10L, result.anchor.timeOffsetMs)
        assertEquals(5, result.kineticChain.stages.size)
        assertTrue(result.kineticChain.isSequential)
        assertEquals(RacketFaceState.SQUARE, result.racketImpact.faceState)
        assertEquals(1, result.visionPoses.size)
        assertEquals(1, result.imuSamples.size)
        assertNotNull(result.diagnosis)
        assertEquals(2, result.diagnosis?.diagnosisTags?.size)
    }

    @Test
    fun `empty poses and imu does not throw and returns low-confidence unsynchronized swing`() {
        val engine: FusionEngine = StubFusionEngine()

        val result = engine.fuse(
            drillType = DrillType.SERVE,
            poses = emptyList(),
            imuSamples = emptyList(),
        )

        assertEquals(0f, result.anchor.confidence, 0.001f)
        assertFalse(result.anchor.isSynchronized)
        assertEquals(DrillType.SERVE, result.drillType)
        assertTrue(result.visionPoses.isEmpty())
        assertTrue(result.imuSamples.isEmpty())
        assertEquals(5, result.kineticChain.stages.size)
    }

    @Test
    fun `empty poses with imu samples does not throw and returns low-confidence unsynchronized swing`() {
        val engine: FusionEngine = StubFusionEngine()
        val imu = listOf(
            ImuDataPoint(1000L, 0f, 9.8f, 0f, 0f, 0f, 0f),
        )

        val result = engine.fuse(
            drillType = DrillType.FOREHAND_VOLLEY,
            poses = emptyList(),
            imuSamples = imu,
        )

        assertEquals(0f, result.anchor.confidence, 0.001f)
        assertFalse(result.anchor.isSynchronized)
        assertTrue(result.visionPoses.isEmpty())
        assertEquals(1, result.imuSamples.size)
    }

    @Test
    fun `empty imu with poses does not throw and returns low-confidence unsynchronized swing`() {
        val engine: FusionEngine = StubFusionEngine()
        val poses = listOf(PoseFrame(landmarks = listOf(PoseLandmark(0.1f, 0.2f, 0.3f, 0.9f))))

        val result = engine.fuse(
            drillType = DrillType.BACKHAND,
            poses = poses,
            imuSamples = emptyList(),
        )

        assertEquals(0f, result.anchor.confidence, 0.001f)
        assertFalse(result.anchor.isSynchronized)
        assertEquals(1, result.visionPoses.size)
        assertTrue(result.imuSamples.isEmpty())
    }
}
