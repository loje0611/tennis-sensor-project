package io.github.loje0611.tennisdoc.feature.lab.ui

import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport
import io.github.loje0611.tennisdoc.core.fusion.anomaly.FatigueAnalysis
import io.github.loje0611.tennisdoc.core.fusion.anomaly.PersonalBaseline
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
import io.github.loje0611.tennisdoc.feature.lab.pipeline.LabFusionPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LabViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeLabFusionPipeline : LabFusionPipeline {
        private val _latestFusedSwing = MutableStateFlow<FusedSwing?>(null)
        override val latestFusedSwing: StateFlow<FusedSwing?> = _latestFusedSwing.asStateFlow()

        private val _latestAnomalyReport = MutableStateFlow<BaselineComparisonReport?>(null)
        override val latestAnomalyReport: StateFlow<BaselineComparisonReport?> = _latestAnomalyReport.asStateFlow()

        private val _currentBaseline = MutableStateFlow<PersonalBaseline?>(null)
        override val currentBaseline: StateFlow<PersonalBaseline?> = _currentBaseline.asStateFlow()

        var fedPoses = mutableListOf<PoseFrame>()
        var fedImu = mutableListOf<ImuDataPoint>()

        override fun feedPoseFrame(frame: PoseFrame) {
            fedPoses.add(frame)
        }

        override fun feedImuSample(sample: ImuDataPoint) {
            fedImu.add(sample)
        }

        override suspend fun onSwingTriggered(sessionId: String, drillType: DrillType): FusedSwing {
            val stages = listOf(
                KineticStage(KineticStageType.HIP, 1000L, 10f),
                KineticStage(KineticStageType.SHOULDER, 1030L, 15f, 30L),
                KineticStage(KineticStageType.WRIST, 1060L, 20f, 30L),
                KineticStage(KineticStageType.RACKET, 1090L, 1500f, 30L),
                KineticStage(KineticStageType.IMPACT, 1110L, 25f, 20L)
            )
            val chain = KineticChain5Stage(stages, true, 110L, 90f)
            val swing = FusedSwing(
                swingId = "test-swing",
                sessionId = sessionId,
                drillType = drillType,
                anchor = SyncAnchor(1000L, 1000L, 0L, 0.9f, true),
                kineticChain = chain,
                racketImpact = RacketImpactOrientation(0f, 0f, 0f, RacketFaceState.SQUARE, 0f),
                visionPoses = emptyList(),
                imuSamples = emptyList()
            )
            _latestFusedSwing.value = swing
            _latestAnomalyReport.value = BaselineComparisonReport(
                drillType = drillType,
                anomalies = emptyList(),
                fatigue = FatigueAnalysis(0f, false, null),
                coachingRecommendation = "Good"
            )
            return swing
        }

        override fun reset() {
            fedPoses.clear()
            fedImu.clear()
            _latestFusedSwing.value = null
            _latestAnomalyReport.value = null
        }
    }

    @Test
    fun `AC-6 LabViewModel feeds data and triggers swing and exposes pipeline StateFlows`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val viewModel = LabViewModel(fakePipeline)

        val pose = PoseFrame(landmarks = listOf(PoseLandmark(0.5f, 0.5f, 0f, 1f)))
        val imu = ImuDataPoint(100L, 1f, 0f, 0f, 0f, 0f, 0f)

        viewModel.onPoseDetected(pose)
        viewModel.onImuReceived(imu)

        assertEquals(1, fakePipeline.fedPoses.size)
        assertEquals(1, fakePipeline.fedImu.size)

        viewModel.triggerSwing("session-1", DrillType.FOREHAND_FLAT)
        testScheduler.advanceUntilIdle()

        assertNotNull(viewModel.latestFusedSwing.value)
        assertEquals("session-1", viewModel.latestFusedSwing.value?.sessionId)
        assertNotNull(viewModel.latestAnomalyReport.value)
    }
}
