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
import org.junit.Assert.assertNotEquals
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

        private val _latestRecordedId = MutableStateFlow<Long?>(null)
        override val latestRecordedId: StateFlow<Long?> = _latestRecordedId.asStateFlow()

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
            _latestRecordedId.value = 101L
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
            _latestRecordedId.value = null
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
        viewModel.selectDrill(DrillType.BACKHAND)
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
        assertEquals(DrillType.FOREHAND, ui.latestAnomalyReport!!.drillType)
    }

    @Test
    fun `AC-5 fatigued or critical anomaly report is exposed on uiState`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)
        testScheduler.advanceUntilIdle()

        fakePipeline.emitAnomalyReport(
            BaselineComparisonReport(
                drillType = DrillType.FOREHAND,
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

    @Test
    fun `TASK-041 AC-1 camera facing mode toggles between FRONT and BACK`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)
        testScheduler.advanceUntilIdle()

        assertEquals(CameraFacingMode.FRONT, viewModel.uiState.value.cameraFacingMode)

        viewModel.toggleCameraFacing()
        testScheduler.advanceUntilIdle()
        assertEquals(CameraFacingMode.BACK, viewModel.uiState.value.cameraFacingMode)

        viewModel.toggleCameraFacing()
        testScheduler.advanceUntilIdle()
        assertEquals(CameraFacingMode.FRONT, viewModel.uiState.value.cameraFacingMode)
    }

    @Test
    fun `TASK-041 AC-3 FRONT camera startSession triggers 5 second countdown before starting`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)
        testScheduler.advanceUntilIdle()

        viewModel.setCameraFacing(CameraFacingMode.FRONT)
        val started = viewModel.startSession()
        assertTrue(started)

        testScheduler.runCurrent()
        assertEquals(5, viewModel.uiState.value.countdownSeconds)
        assertFalse(viewModel.uiState.value.isSessionActive)

        for (expected in 4 downTo 1) {
            testScheduler.advanceTimeBy(1000L)
            testScheduler.runCurrent()
            assertEquals(expected, viewModel.uiState.value.countdownSeconds)
            assertFalse(viewModel.uiState.value.isSessionActive)
        }

        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(0, viewModel.uiState.value.countdownSeconds)
        assertFalse(viewModel.uiState.value.isSessionActive)

        testScheduler.advanceTimeBy(500L)
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.countdownSeconds)
        assertTrue(viewModel.uiState.value.isSessionActive)
    }

    @Test
    fun `TASK-041 AC-4 BACK camera startSession starts immediately without countdown`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)
        testScheduler.advanceUntilIdle()

        viewModel.setCameraFacing(CameraFacingMode.BACK)
        val started = viewModel.startSession()
        assertTrue(started)

        testScheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.countdownSeconds)
        assertTrue(viewModel.uiState.value.isSessionActive)
    }

    @Test
    fun `TASK-041 cancelCountdown stops countdown and session does not start`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)
        testScheduler.advanceUntilIdle()

        viewModel.setCameraFacing(CameraFacingMode.FRONT)
        viewModel.startSession()
        testScheduler.runCurrent()
        assertEquals(5, viewModel.uiState.value.countdownSeconds)

        viewModel.cancelCountdown()
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.countdownSeconds)
        assertFalse(viewModel.uiState.value.isSessionActive)
    }

    @Test
    fun `TASK-041 AC-5 FRONT camera swing produces farFieldHud with auto timeout`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)
        testScheduler.advanceUntilIdle()

        viewModel.setCameraFacing(CameraFacingMode.FRONT)
        testScheduler.advanceUntilIdle()

        viewModel.triggerSwing()
        testScheduler.runCurrent()

        val hud = viewModel.uiState.value.farFieldHud
        assertNotNull(hud)
        assertTrue(hud!!.isSquare)
        assertEquals(90f, hud.energyEfficiency)

        // After 3 seconds, HUD is cleared
        testScheduler.advanceTimeBy(3000L)
        testScheduler.runCurrent()
        assertEquals(null, viewModel.uiState.value.farFieldHud)
    }

    @Test
    fun `TASK-041 AC-5 BACK camera swing does not produce farFieldHud`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)
        viewModel.setCameraFacing(CameraFacingMode.BACK)
        testScheduler.advanceUntilIdle()

        viewModel.triggerSwing()
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.farFieldHud)
    }

    @Test
    fun `TASK-041 AC-7 finishSession produces SessionCompletionSummary and dismiss clears it`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)
        viewModel.setCameraFacing(CameraFacingMode.BACK)
        viewModel.startSession()
        testScheduler.advanceUntilIdle()

        viewModel.triggerSwing()
        testScheduler.advanceUntilIdle()

        viewModel.finishSession()
        testScheduler.advanceUntilIdle()

        val summary = viewModel.uiState.value.completionSummary
        assertNotNull(summary)
        assertEquals(1, summary!!.totalSwingCount)
        assertEquals(100, summary.squareRatePercent)
        assertEquals(90f, summary.averageEnergyEfficiency)
        assertEquals(101L, summary.latestRecordId)

        viewModel.dismissCompletionSummary()
        testScheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.completionSummary)
    }

    @Test
    fun `TASK-041 AC-6 FRONT camera swing triggers TTS utterance and BACK camera swing is muted`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val fakeAudio = object : io.github.loje0611.tennisdoc.feature.lab.audio.LabAudioFeedbackPort {
            var spoken: String? = null
            var beepPlayed: Boolean = false
            override val lastSpokenUtterance: String? get() = spoken
            override fun speakCoaching(text: String) { spoken = text }
            override fun playImpactBeep() { beepPlayed = true }
            override fun playCountdownTick(second: Int) {}
            override fun playCountdownStart() {}
            override fun release() {}
        }
        val viewModel = LabViewModel(fakePipeline, fakePort, fakeAudio)
        testScheduler.advanceUntilIdle()

        // 1. FRONT camera: TTS is spoken
        viewModel.setCameraFacing(CameraFacingMode.FRONT)
        testScheduler.advanceUntilIdle()
        viewModel.triggerSwing()
        testScheduler.advanceUntilIdle()
        assertEquals("훌륭한 임팩트입니다.", fakeAudio.spoken)
        assertFalse(fakeAudio.beepPlayed)

        // 2. BACK camera: TTS is muted, impact beep is played
        fakeAudio.spoken = null
        fakeAudio.beepPlayed = false
        viewModel.setCameraFacing(CameraFacingMode.BACK)
        testScheduler.advanceUntilIdle()
        viewModel.triggerSwing()
        testScheduler.advanceUntilIdle()
        assertEquals(null, fakeAudio.spoken) // Muted
        assertTrue(fakeAudio.beepPlayed)
    }

    @Test
    fun `TASK-041 onPoseDetected detects body framing status`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        val viewModel = LabViewModel(fakePipeline, fakePort)

        // Landmarks without enough points
        val incompletePose = PoseFrame(landmarks = listOf(PoseLandmark(0.5f, 0.5f, 0f, 0.9f)))
        viewModel.onPoseDetected(incompletePose)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isBodyFramed)

        // Full 33 landmarks with key points visible
        val fullLandmarks = (0 until 33).map { PoseLandmark(0.5f, 0.5f, 0f, 0.9f) }
        viewModel.onPoseDetected(PoseFrame(landmarks = fullLandmarks))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isBodyFramed)
    }
    @Test
    fun `TASK-048 AC-5 requestAiCoachReport triggers loading and updates state with report`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()

        var savedSessionId: String? = null
        var savedReportJson: String? = null

        val fakeRepo = object : io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository {
            override fun observeSessions() = kotlinx.coroutines.flow.emptyFlow<List<io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity>>()
            override suspend fun generateCsvString(sId: String?, sTime: Long?, eTime: Long?) = ""
            override suspend fun getSessionDetail(sessionId: String) = null
            override suspend fun deleteSession(sessionId: String) {}
            override suspend fun startSession(type: SessionType, drill: DrillType?, time: Long) = ""
            override suspend fun insertProvisionalSession(session: io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity) {}
            override suspend fun finalizeSession(sId: String, eTime: Long, c: Int, d: Long, f: Int, b: Int, map: Map<String, Int>) {}
            override suspend fun insertSessionWithBreakdown(s: io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity, map: List<Pair<String, Int>>) {}
            override suspend fun insertMockSession(s: io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity, map: Map<String, Int>, events: List<io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity>) {}
            override suspend fun insertSwingEvent(event: io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity) {}
            override suspend fun getAverageMetrics(sessionId: String, categoryKey: String) = null
            override suspend fun getSwingEventsForSession(sessionId: String) = emptyList<io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity>()
            override suspend fun updateGlobalStatistics(categoryKey: String, metrics: io.github.loje0611.tennisdoc.core.model.SwingMetrics) {}
            override suspend fun batchUpdateGlobalStatistics(events: List<io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity>) {}
            override suspend fun getGlobalAverageMetrics(categoryKey: String) = null
            override fun getLabRawRecordsForSession(sessionId: String) = kotlinx.coroutines.flow.emptyFlow<List<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>>()
            override suspend fun getLabRawRecordById(recordId: Long) = null
            override suspend fun insertLabRawRecord(record: io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity) = 1L

            override suspend fun saveAiCoachReport(sessionId: String, reportJson: String, generatedAt: Long) {
                savedSessionId = sessionId
                savedReportJson = reportJson
            }
        }

        val viewModel = LabViewModel(
            pipeline = fakePipeline,
            sessionPort = fakePort,
            audioPort = io.github.loje0611.tennisdoc.feature.lab.audio.DefaultLabAudioFeedbackPort(),
            aiCoachService = io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService(),
            swingHistoryRepository = fakeRepo
        )

        viewModel.setCameraFacing(CameraFacingMode.BACK)
        viewModel.startSession()
        testScheduler.advanceUntilIdle()
        viewModel.triggerSwing()
        testScheduler.advanceUntilIdle()
        viewModel.finishSession()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGeneratingAiReport)
        org.junit.Assert.assertNull(viewModel.uiState.value.aiCoachReport)

        viewModel.requestAiCoachReport()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGeneratingAiReport)
        val report = viewModel.uiState.value.aiCoachReport
        assertNotNull(report)
        assertTrue(report!!.isFallbackReport)
        assertTrue(report.overallSummary.isNotBlank())

        assertEquals("test-session-id", savedSessionId)
        assertNotNull(savedReportJson)
        assertNotEquals("{}", savedReportJson)
        assertTrue(
            "persisted reportJson must contain the generated overallSummary, not a stub object",
            savedReportJson!!.contains(report.overallSummary)
        )
    }

    @Test
    fun `TASK-050 FR-4 requestAiCoachReport uses preferences tone and blank key fallback`() = runTest {
        val fakePipeline = FakeLabFusionPipeline()
        val fakePort = FakeLabSessionPort()
        var savedReportJson: String? = null
        val fakeRepo = object : io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository {
            override fun observeSessions() = kotlinx.coroutines.flow.emptyFlow<List<io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity>>()
            override suspend fun generateCsvString(sId: String?, sTime: Long?, eTime: Long?) = ""
            override suspend fun getSessionDetail(sessionId: String) = null
            override suspend fun deleteSession(sessionId: String) {}
            override suspend fun startSession(type: SessionType, drill: DrillType?, time: Long) = ""
            override suspend fun insertProvisionalSession(session: io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity) {}
            override suspend fun finalizeSession(sId: String, eTime: Long, c: Int, d: Long, f: Int, b: Int, map: Map<String, Int>) {}
            override suspend fun insertSessionWithBreakdown(s: io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity, map: List<Pair<String, Int>>) {}
            override suspend fun insertMockSession(s: io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity, map: Map<String, Int>, events: List<io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity>) {}
            override suspend fun insertSwingEvent(event: io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity) {}
            override suspend fun getAverageMetrics(sessionId: String, categoryKey: String) = null
            override suspend fun getSwingEventsForSession(sessionId: String) = emptyList<io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity>()
            override suspend fun updateGlobalStatistics(categoryKey: String, metrics: io.github.loje0611.tennisdoc.core.model.SwingMetrics) {}
            override suspend fun batchUpdateGlobalStatistics(events: List<io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity>) {}
            override suspend fun getGlobalAverageMetrics(categoryKey: String) = null
            override fun getLabRawRecordsForSession(sessionId: String) = kotlinx.coroutines.flow.emptyFlow<List<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>>()
            override suspend fun getLabRawRecordById(recordId: Long) = null
            override suspend fun insertLabRawRecord(record: io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity) = 1L
            override suspend fun saveAiCoachReport(sessionId: String, reportJson: String, generatedAt: Long) {
                savedReportJson = reportJson
            }
        }
        val fakePrefs = object : io.github.loje0611.tennisdoc.core.data.repository.AiCoachPreferencesRepository {
            override val geminiApiKey = MutableStateFlow<String?>(null)
            override val llmProvider = MutableStateFlow(io.github.loje0611.tennisdoc.core.model.LlmProvider.GEMINI)
            override val defaultCoachTone = MutableStateFlow(io.github.loje0611.tennisdoc.core.model.CoachTone.STRICT)
            override suspend fun setGeminiApiKey(apiKey: String?) {}
            override suspend fun setLlmProvider(provider: io.github.loje0611.tennisdoc.core.model.LlmProvider) {}
            override suspend fun setDefaultCoachTone(tone: io.github.loje0611.tennisdoc.core.model.CoachTone) {}
        }

        val viewModel = LabViewModel(
            pipeline = fakePipeline,
            sessionPort = fakePort,
            audioPort = io.github.loje0611.tennisdoc.feature.lab.audio.DefaultLabAudioFeedbackPort(),
            aiCoachService = io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService(),
            swingHistoryRepository = fakeRepo,
            aiCoachPreferences = fakePrefs,
        )
        viewModel.setCameraFacing(CameraFacingMode.BACK)
        viewModel.startSession()
        testScheduler.advanceUntilIdle()
        viewModel.triggerSwing()
        testScheduler.advanceUntilIdle()
        viewModel.finishSession()
        testScheduler.advanceUntilIdle()

        viewModel.requestAiCoachReport()
        testScheduler.advanceUntilIdle()

        val report = viewModel.uiState.value.aiCoachReport
        assertNotNull(report)
        assertTrue(report!!.isFallbackReport)
        assertTrue(
            "blank API key + STRICT default tone must produce the strict fallback summary",
            report.overallSummary.startsWith("결과에 집중해야 합니다."),
        )
        assertNotNull(savedReportJson)
        assertTrue(savedReportJson!!.contains(report.overallSummary))
    }
}
