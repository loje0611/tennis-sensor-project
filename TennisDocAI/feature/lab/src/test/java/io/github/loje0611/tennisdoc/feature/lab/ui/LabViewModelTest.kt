package io.github.loje0611.tennisdoc.feature.lab.ui

import io.github.loje0611.tennisdoc.core.fusion.anomaly.AnomalyResult
import io.github.loje0611.tennisdoc.core.fusion.anomaly.AnomalySeverity
import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport
import io.github.loje0611.tennisdoc.core.fusion.anomaly.FatigueAnalysis
import io.github.loje0611.tennisdoc.core.fusion.anomaly.PersonalBaseline
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
import io.github.loje0611.tennisdoc.core.model.SessionType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import io.github.loje0611.tennisdoc.feature.lab.pipeline.LabFusionPipeline
import io.github.loje0611.tennisdoc.feature.lab.session.LabSessionPort
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    private class FakeLabSessionPort : LabSessionPort {
        private val _isSessionActive = MutableStateFlow(false)
        override val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

        private val _activeSessionId = MutableStateFlow<String?>(null)
        override val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

        private val _sessionDurationSeconds = MutableStateFlow(0L)
        override val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

        private val _swingCount = MutableStateFlow(0)
        override val swingCount: StateFlow<Int> = _swingCount.asStateFlow()

        private val _isSensorConnected = MutableStateFlow(true)
        override val isSensorConnected: StateFlow<Boolean> = _isSensorConnected.asStateFlow()

        private val _isSensorScanning = MutableStateFlow(false)
        override val isSensorScanning: StateFlow<Boolean> = _isSensorScanning.asStateFlow()

        private val _isDebugModeEnabled = MutableStateFlow(false)
        override val isDebugModeEnabled: StateFlow<Boolean> = _isDebugModeEnabled.asStateFlow()

        var lastStartType: SessionType? = null
        var lastStartDrill: DrillType? = null
        var connectCalled = false
        var disconnectCalled = false

        fun setSensorConnected(connected: Boolean) {
            _isSensorConnected.value = connected
            if (connected) _isSensorScanning.value = false
        }

        fun setDebugModeEnabled(enabled: Boolean) {
            _isDebugModeEnabled.value = enabled
        }

        override fun startSession(type: SessionType, drillType: DrillType): String {
            lastStartType = type
            lastStartDrill = drillType
            val sid = "test-session-id"
            _activeSessionId.value = sid
            _isSessionActive.value = true
            return sid
        }

        override fun finishSession() {
            _activeSessionId.value = null
            _isSessionActive.value = false
        }

        override fun connectSensor() {
            connectCalled = true
            _isSensorScanning.value = true
        }

        override fun disconnectSensor() {
            disconnectCalled = true
            _isSensorScanning.value = false
            _isSensorConnected.value = false
        }
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
            val diagnosis = FusionDiagnosis(
                diagnosisTags = listOf("CLEAN_STRIKE"),
                primaryCause = "정상 스윙",
                coachingFeedback = "훌륭한 임팩트입니다.",
                causalExplanation = "운동 체인이 순차적으로 올바르게 전달되었습니다."
            )
            val swing = FusedSwing(
                swingId = "test-swing",
                sessionId = sessionId,
                drillType = drillType,
                anchor = SyncAnchor(1000L, 1000L, 0L, 0.9f, true),
                kineticChain = chain,
                racketImpact = RacketImpactOrientation(0f, 0f, 0f, RacketFaceState.SQUARE, 0f),
                visionPoses = emptyList(),
                imuSamples = emptyList(),
                diagnosis = diagnosis
            )
            _latestFusedSwing.value = swing
            _latestAnomalyReport.value = BaselineComparisonReport(
                drillType = drillType,
                anomalies = listOf(
                    AnomalyResult(
                        metricKey = "racketSpeed",
                        currentValue = 1500f,
                        baselineMean = 1500f,
                        zScore = 0f,
                        isAnomaly = false,
                        severity = AnomalySeverity.NORMAL,
                        description = "정상"
                    )
                ),
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

        fun emitAnomalyReport(report: BaselineComparisonReport) {
            _latestAnomalyReport.value = report
        }
    }

    @Test
    fun `AC-2 drill selection updates selectedDrill and is disabled when session is active`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)

        viewModel.selectDrill(DrillType.SERVE)
        assertEquals(DrillType.SERVE, viewModel.selectedDrill.value)

        viewModel.startSession()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSessionActive)

        // Drill selection attempt while session is active should be ignored
        viewModel.selectDrill(DrillType.BACKHAND_TOPSPIN)
        assertEquals(DrillType.SERVE, viewModel.selectedDrill.value)
    }

    @Test
    fun `AC-3 startSession and finishSession correctly toggle session state`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)

        assertFalse(viewModel.uiState.value.isSessionActive)

        viewModel.selectDrill(DrillType.SERVE)
        viewModel.startSession()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSessionActive)
        assertEquals("test-session-id", viewModel.uiState.value.activeSessionId)
        assertEquals(SessionType.LAB, fakePort.lastStartType)
        assertEquals(DrillType.SERVE, fakePort.lastStartDrill)

        viewModel.finishSession()
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSessionActive)
    }

    @Test
    fun `AC-4 AC-5 FusedSwing and AnomalyReport data update UI state`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)

        viewModel.startSession()
        testScheduler.advanceUntilIdle()

        viewModel.triggerSwing()
        testScheduler.advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertNotNull(ui.latestFusedSwing)
        assertEquals(RacketFaceState.SQUARE, ui.latestFusedSwing!!.racketImpact.faceState)
        assertEquals("훌륭한 임팩트입니다.", ui.latestFusedSwing!!.diagnosis?.coachingFeedback)

        assertNotNull(ui.latestAnomalyReport)
        assertEquals(DrillType.FOREHAND_TOPSPIN, ui.latestAnomalyReport!!.drillType)
    }

    @Test
    fun `AC-5 fatigued or critical anomaly report is exposed on uiState`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)
        testScheduler.advanceUntilIdle()

        fakePipeline.emitAnomalyReport(
            BaselineComparisonReport(
                drillType = DrillType.FOREHAND_TOPSPIN,
                anomalies = listOf(
                    AnomalyResult(
                        metricKey = "racketSpeed",
                        currentValue = 800f,
                        baselineMean = 1500f,
                        zScore = -2.8f,
                        isAnomaly = true,
                        severity = AnomalySeverity.CRITICAL,
                        description = "라켓 스피드가 평소 대비 2.8σ 급락했습니다."
                    )
                ),
                fatigue = FatigueAnalysis(
                    fatigueScore = 0.9f,
                    isFatigued = true,
                    formBreakdownSummary = "⚠️ 라켓 스피드가 평소 대비 2.8σ 급락했습니다. 휴식을 권장합니다."
                ),
                coachingRecommendation = "휴식"
            )
        )
        testScheduler.advanceUntilIdle()

        val report = viewModel.uiState.value.latestAnomalyReport
        assertNotNull(report)
        assertTrue(report!!.fatigue.isFatigued)
        assertTrue(report.anomalies.any { it.severity == AnomalySeverity.CRITICAL })
    }

    @Test
    fun `LabViewModel feeds pose and imu into pipeline`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val viewModel = LabViewModel(fakePipeline)

        val pose = PoseFrame(landmarks = listOf(PoseLandmark(0.5f, 0.5f, 0f, 1f)))
        val imu = ImuDataPoint(100L, 1f, 0f, 0f, 0f, 0f, 0f)

        viewModel.onPoseDetected(pose)
        viewModel.onImuReceived(imu)

        assertEquals(1, fakePipeline.fedPoses.size)
        assertEquals(1, fakePipeline.fedImu.size)
    }

    @Test
    fun startSessionIsRejectedWhenSensorDisconnected() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        fakePort.setSensorConnected(false)
        val viewModel = LabViewModel(fakePipeline, fakePort)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.startSession())
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSessionActive)
        assertEquals(null, fakePort.lastStartType)
        assertTrue(fakePort.connectCalled)
    }

    @Test
    fun connectSensorForwardsToSessionPort() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        fakePort.setSensorConnected(false)
        val viewModel = LabViewModel(fakePipeline, fakePort)

        viewModel.connectSensor()
        testScheduler.advanceUntilIdle()
        assertTrue(fakePort.connectCalled)
        assertTrue(viewModel.uiState.value.isSensorScanning)
    }

    @Test
    fun `AC-10 isDebugModeEnabled updates uiState`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDebugModeEnabled)

        fakePort.setDebugModeEnabled(true)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDebugModeEnabled)
    }
}
