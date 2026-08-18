package io.github.loje0611.tennisdoc.feature.lab.replay

import io.github.loje0611.tennisdoc.core.fusion.model.FusionDiagnosis
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
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
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LabReplayViewModelTest {

    private lateinit var viewModel: LabReplayViewModel
    private lateinit var sampleSwing: FusedSwing

    @Before
    fun setUp() {
        viewModel = LabReplayViewModel()

        val sampleLandmarks = List(33) {
            PoseLandmark(x = 0.5f, y = 0.5f, z = 0f, visibility = 0.9f)
        }
        val poses = List(30) {
            PoseFrame(
                landmarks = sampleLandmarks
            )
        }
        val imuPoints = List(50) { idx ->
            ImuDataPoint(
                timestampMs = 1000L + idx * 20L,
                accelX = 1.0f,
                accelY = 2.0f,
                accelZ = 0.5f,
                gyroX = 10f,
                gyroY = 20f,
                gyroZ = 5f
            )
        }

        sampleSwing = FusedSwing(
            swingId = "swing_001",
            sessionId = "test_lab_session",
            drillType = DrillType.FOREHAND,
            anchor = SyncAnchor(
                visionImpactTimestampMs = 495L,
                sensorImpactTimestampMs = 1500L,
                timeOffsetMs = 1005L,
                confidence = 0.95f,
                isSynchronized = true
            ),
            kineticChain = KineticChain5Stage(
                stages = listOf(
                    KineticStage(KineticStageType.HIP, 1400L, 100f),
                    KineticStage(KineticStageType.SHOULDER, 1440L, 120f),
                    KineticStage(KineticStageType.WRIST, 1480L, 150f),
                    KineticStage(KineticStageType.RACKET, 1500L, 200f),
                    KineticStage(KineticStageType.IMPACT, 1500L, 250f)
                ),
                isSequential = true,
                totalDurationMs = 100L,
                energyTransferEfficiency = 0.925f
            ),
            racketImpact = RacketImpactOrientation(
                rollDeg = 0f,
                pitchDeg = 1.5f,
                yawDeg = 0f,
                faceState = RacketFaceState.SQUARE,
                deviationDeg = 2.0f
            ),
            diagnosis = FusionDiagnosis(
                diagnosisTags = listOf("CLEAN_STRIKE", "SQUARE_FACE"),
                primaryCause = "OPTIMAL_TIMING",
                coachingFeedback = "훌륭한 타이밍입니다. 동일한 템포를 유지하세요.",
                causalExplanation = "골반-어깨-손목-라켓 순차 가속이 정확하며 최적의 임팩트를 형성했습니다."
            ),
            visionPoses = poses,
            imuSamples = imuPoints
        )
    }

    @Test
    fun setFusedSwing_initializesUiStateCorrectly() {
        viewModel.setFusedSwing(sampleSwing)
        val state = viewModel.uiState.value

        assertNotNull(state.fusedSwing)
        assertTrue(state.durationMs >= 957L)
        assertEquals(0L, state.currentTimestampMs)
        assertEquals(495L, state.impactTimestampMs)
        assertFalse(state.isPlaying)
        assertEquals(1.0f, state.playbackSpeed, 0.001f)
        assertNotNull(state.currentPoseFrame)
        assertNotNull(state.currentImuPoint)
    }

    @Test
    fun seekTo_updatesCurrentPoseAndImuWithSyncAnchorOffset() {
        viewModel.setFusedSwing(sampleSwing)

        // 임팩트 시점으로 이동
        viewModel.seekTo(495L)
        val state = viewModel.uiState.value

        assertEquals(495L, state.currentTimestampMs)
        assertTrue("isImpactFrame should be true near 495ms", state.isImpactFrame)
        assertNotNull(state.currentPoseFrame)
        assertNotNull(state.currentImuPoint)
        assertEquals(
            "IMU cursor must lock to t_current + timeOffsetMs (sensor impact 1500ms)",
            1500L,
            state.currentImuPoint!!.timestampMs,
        )
        assertTrue("tooltips should be populated on impact frame", state.tooltips.isNotEmpty())
    }

    @Test
    fun jumpToImpact_seeksDirectlyToImpactTimestamp() {
        viewModel.setFusedSwing(sampleSwing)
        viewModel.seekTo(0L)

        viewModel.jumpToImpact()
        val state = viewModel.uiState.value

        assertEquals(495L, state.currentTimestampMs)
        assertTrue(state.isImpactFrame)
    }

    @Test
    fun stepForwardAndStepBackward_adjustTimestampBy33Ms() {
        viewModel.setFusedSwing(sampleSwing)
        viewModel.seekTo(100L)

        viewModel.stepForward()
        assertEquals(133L, viewModel.uiState.value.currentTimestampMs)

        viewModel.stepBackward()
        assertEquals(100L, viewModel.uiState.value.currentTimestampMs)

        viewModel.seekTo(0L)
        viewModel.stepBackward()
        assertEquals("clamped to 0", 0L, viewModel.uiState.value.currentTimestampMs)
    }

    @Test
    fun playbackSpeed_canBeToggled() {
        viewModel.setFusedSwing(sampleSwing)
        viewModel.setPlaybackSpeed(0.5f)
        assertEquals(0.5f, viewModel.uiState.value.playbackSpeed, 0.001f)

        viewModel.setPlaybackSpeed(1.0f)
        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun togglePlay_startsAndPausesPlayback() {
        viewModel.setFusedSwing(sampleSwing)
        assertFalse(viewModel.uiState.value.isPlaying)

        viewModel.togglePlay()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.togglePlay()
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun setFusedSwing_withoutExistingFile_setsHasVideoFalse() {
        viewModel.setFusedSwing(sampleSwing, videoPath = "/tmp/missing-swing-${System.nanoTime()}.mp4")
        val state = viewModel.uiState.value
        assertFalse(state.hasVideo)
        assertNull(state.videoPath)
        assertEquals("🟢 정타 (스퀘어)", state.faceStateLabel)
        assertTrue(state.swingTrailPoints.isNotEmpty())
    }

    @Test
    fun setFusedSwing_withExistingFile_setsHasVideoAndKoreanFaceLabel() {
        val video = File.createTempFile("replay-vm", ".mp4")
        try {
            video.writeBytes(byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70))
            viewModel.setFusedSwing(sampleSwing, videoPath = video.absolutePath)
            val state = viewModel.uiState.value
            assertTrue(state.hasVideo)
            assertEquals(video.absolutePath, state.videoPath)
            assertEquals("🟢 정타 (스퀘어)", state.faceStateLabel)
            assertEquals("훌륭한 타이밍입니다. 동일한 템포를 유지하세요.", state.coachingOneLiner)
        } finally {
            video.delete()
        }
    }
}
