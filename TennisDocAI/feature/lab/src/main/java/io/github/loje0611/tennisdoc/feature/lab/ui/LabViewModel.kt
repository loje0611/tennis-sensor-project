package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.feature.lab.audio.DefaultLabAudioFeedbackPort
import io.github.loje0611.tennisdoc.feature.lab.audio.LabAudioFeedbackPort
import io.github.loje0611.tennisdoc.feature.lab.pipeline.LabFusionPipeline
import io.github.loje0611.tennisdoc.feature.lab.session.LabSessionPort
import io.github.loje0611.tennisdoc.core.data.repository.AiCoachPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class LabViewModel @Inject constructor(
    val pipeline: LabFusionPipeline,
    val sessionPort: LabSessionPort? = null,
    val audioPort: LabAudioFeedbackPort = DefaultLabAudioFeedbackPort(),
    val aiCoachService: io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService? = null,
    val swingHistoryRepository: io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository? = null,
    val aiCoachPreferences: io.github.loje0611.tennisdoc.core.data.repository.AiCoachPreferencesRepository? = null
) : ViewModel() {

    private val _selectedDrill = MutableStateFlow(DrillType.FOREHAND)
    val selectedDrill: StateFlow<DrillType> = _selectedDrill.asStateFlow()

    private val _localIsSessionActive = MutableStateFlow(false)
    private val _localSessionId = MutableStateFlow<String?>(null)
    private val _localSessionDuration = MutableStateFlow(0L)
    private val _localSwingCount = MutableStateFlow(0)
    private val _localSensorConnected = MutableStateFlow(false)
    private val _localSensorScanning = MutableStateFlow(false)
    private val _localDebugModeEnabled = MutableStateFlow(false)

    private val _cameraFacingMode = MutableStateFlow(CameraFacingMode.FRONT)
    private val _countdownSeconds = MutableStateFlow<Int?>(null)
    private val _farFieldHud = MutableStateFlow<FarFieldHudState?>(null)
    private val _completionSummary = MutableStateFlow<SessionCompletionSummary?>(null)
    private val _isBodyFramed = MutableStateFlow(false)
    private val _aiCoachReport = MutableStateFlow<io.github.loje0611.tennisdoc.core.model.AiCoachReport?>(null)
    private val _isGeneratingAiReport = MutableStateFlow(false)

    private val sessionSwings = mutableListOf<FusedSwing>()
    private var countdownJob: Job? = null
    private var hudJob: Job? = null

    val uiState: StateFlow<LabUiState> = combine(
        combine(
            _selectedDrill,
            sessionPort?.isSessionActive ?: _localIsSessionActive,
            sessionPort?.activeSessionId ?: _localSessionId,
            sessionPort?.sessionDurationSeconds ?: _localSessionDuration,
            sessionPort?.swingCount ?: _localSwingCount,
            sessionPort?.isSensorConnected ?: _localSensorConnected,
            sessionPort?.isSensorScanning ?: _localSensorScanning
        ) { arr -> arr },
        combine(
            sessionPort?.isDebugModeEnabled ?: _localDebugModeEnabled,
            _cameraFacingMode,
            _countdownSeconds,
            _farFieldHud,
            _completionSummary,
            pipeline.latestFusedSwing,
            pipeline.latestAnomalyReport,
            _isBodyFramed
        ) { arr -> arr },
        combine(
            _aiCoachReport,
            _isGeneratingAiReport
        ) { arr -> arr }
    ) { part1, part2, part3 ->
        @Suppress("UNCHECKED_CAST")
        LabUiState(
            selectedDrill = part1[0] as DrillType,
            isSessionActive = part1[1] as Boolean,
            activeSessionId = part1[2] as String?,
            sessionDurationSeconds = part1[3] as Long,
            swingCount = part1[4] as Int,
            isSensorConnected = part1[5] as Boolean,
            isSensorScanning = part1[6] as Boolean,
            isDebugModeEnabled = part2[0] as Boolean,
            cameraFacingMode = part2[1] as CameraFacingMode,
            countdownSeconds = part2[2] as Int?,
            farFieldHud = part2[3] as FarFieldHudState?,
            completionSummary = part2[4] as SessionCompletionSummary?,
            latestFusedSwing = part2[5] as FusedSwing?,
            latestAnomalyReport = part2[6] as BaselineComparisonReport?,
            isBodyFramed = part2[7] as Boolean,
            aiCoachReport = part3[0] as io.github.loje0611.tennisdoc.core.model.AiCoachReport?,
            isGeneratingAiReport = part3[1] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LabUiState()
    )

    fun toggleCameraFacing() {
        _cameraFacingMode.update {
            if (it == CameraFacingMode.FRONT) CameraFacingMode.BACK else CameraFacingMode.FRONT
        }
    }

    fun setCameraFacing(mode: CameraFacingMode) {
        _cameraFacingMode.value = mode
    }

    fun selectDrill(drillType: DrillType) {
        if (!uiState.value.isSessionActive && _countdownSeconds.value == null) {
            _selectedDrill.value = drillType
        }
    }

    fun connectSensor() {
        sessionPort?.connectSensor()
    }

    fun disconnectSensor() {
        sessionPort?.disconnectSensor()
    }

    fun startSession(): Boolean {
        val connected = sessionPort?.isSensorConnected?.value ?: _localSensorConnected.value
        if (!connected) {
            connectSensor()
            return false
        }
        if (uiState.value.isSessionActive || _countdownSeconds.value != null) {
            return false
        }
        sessionSwings.clear()
        _completionSummary.value = null

        if (_cameraFacingMode.value == CameraFacingMode.FRONT) {
            countdownJob?.cancel()
            countdownJob = viewModelScope.launch {
                for (sec in 5 downTo 1) {
                    _countdownSeconds.value = sec
                    audioPort.playCountdownTick(sec)
                    delay(1000L)
                }
                _countdownSeconds.value = 0
                audioPort.playCountdownStart()
                delay(500L)
                _countdownSeconds.value = null
                executeActualStartSession()
            }
            return true
        } else {
            executeActualStartSession()
            return true
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        _countdownSeconds.value = null
    }

    private fun executeActualStartSession() {
        val drill = _selectedDrill.value
        val port = sessionPort
        if (port != null) {
            port.startSession(SessionType.LAB, drill)
        } else {
            _localIsSessionActive.value = true
            _localSessionId.value = "local-session"
        }
    }

    fun finishSession() {
        countdownJob?.cancel()
        _countdownSeconds.value = null

        val totalSwings = if (sessionSwings.isNotEmpty()) sessionSwings.size else uiState.value.swingCount
        val squareCount = sessionSwings.count { it.racketImpact.faceState.name == "SQUARE" }
        val squareRate = if (totalSwings > 0) (squareCount * 100) / totalSwings else 0
        val avgEfficiency = if (sessionSwings.isNotEmpty()) {
            sessionSwings.map { it.kineticChain.energyTransferEfficiency }.average().toFloat()
        } else 0f

        val currentSessionId = uiState.value.activeSessionId ?: "lab-session"
        val durationSec = uiState.value.sessionDurationSeconds
        val drillName = _selectedDrill.value.toDisplayName()
        val latestId = pipeline.latestRecordedId.value ?: 1L

        _completionSummary.value = SessionCompletionSummary(
            sessionId = currentSessionId,
            drillName = drillName,
            totalSwingCount = totalSwings,
            durationSeconds = durationSec,
            squareRatePercent = squareRate,
            averageEnergyEfficiency = avgEfficiency,
            latestRecordId = latestId
        )

        val port = sessionPort
        if (port != null) {
            port.finishSession()
        } else {
            _localIsSessionActive.value = false
            _localSessionId.value = null
        }
        pipeline.reset()
    }

    fun dismissCompletionSummary() {
        _completionSummary.value = null
    }

    fun onPoseDetected(frame: PoseFrame) {
        pipeline.feedPoseFrame(frame)

        // Body Framing Check: Head(0), Shoulders(11,12), Hips(23,24), Ankles(27,28)
        val lms = frame.landmarks
        if (lms.size >= 29) {
            val keyIndices = listOf(0, 11, 12, 23, 24, 27, 28)
            val isFramed = keyIndices.all { idx ->
                val lm = lms[idx]
                lm.visibility >= 0.4f && !lm.isNan && lm.x in 0.02f..0.98f && lm.y in 0.02f..0.98f
            }
            _isBodyFramed.value = isFramed
        } else {
            _isBodyFramed.value = false
        }
    }

    fun onImuReceived(sample: ImuDataPoint) {
        pipeline.feedImuSample(sample)
    }

    fun triggerSwing(sessionId: String? = null, drillType: DrillType? = null) {
        val targetSessionId = sessionId ?: uiState.value.activeSessionId ?: "temp-session"
        val targetDrill = drillType ?: _selectedDrill.value
        viewModelScope.launch {
            val fused = pipeline.onSwingTriggered(targetSessionId, targetDrill)
            if (fused != null) {
                sessionSwings.add(fused)
                val feedback = fused.diagnosis?.coachingFeedback ?: "훌륭한 임팩트입니다."
                if (_cameraFacingMode.value == CameraFacingMode.FRONT) {
                    audioPort.speakCoaching(feedback)
                    val faceState = fused.racketImpact.faceState.name
                    val isSquare = faceState == "SQUARE"
                    val angle = fused.racketImpact.deviationDeg
                    val faceText = if (angle >= 0) "$faceState +${angle.toInt()}°" else "$faceState ${angle.toInt()}°"
                    val faceColorHex = when (faceState) {
                        "SQUARE" -> 0xFF00E676
                        "OPEN" -> 0xFFFF9100
                        else -> 0xFFFF1744
                    }
                    _farFieldHud.value = FarFieldHudState(
                        faceText = faceText,
                        faceColorHex = faceColorHex,
                        energyEfficiency = fused.kineticChain.energyTransferEfficiency,
                        isSquare = isSquare
                    )
                    hudJob?.cancel()
                    hudJob = launch {
                        delay(3000L)
                        _farFieldHud.value = null
                    }
                } else {
                    audioPort.playImpactBeep()
                    _farFieldHud.value = null
                }
            }
        }
    }

    fun requestAiCoachReport(tone: io.github.loje0611.tennisdoc.core.model.CoachTone? = null) {
        if (aiCoachService == null || swingHistoryRepository == null) return

        val currentSessionId = uiState.value.activeSessionId ?: uiState.value.completionSummary?.sessionId ?: "lab-session"
        val swings = sessionSwings.toList()
        
        if (swings.isEmpty() && uiState.value.swingCount == 0) {
            // 빈 세션이면 안전 처리 혹은 리턴
            return
        }

        viewModelScope.launch {
            _isGeneratingAiReport.value = true
            
            try {
                val apiKey = aiCoachPreferences?.geminiApiKey?.first()
                val provider = aiCoachPreferences?.llmProvider?.first() ?: io.github.loje0611.tennisdoc.core.model.LlmProvider.GEMINI
                val resolvedTone = tone ?: aiCoachPreferences?.defaultCoachTone?.first() ?: io.github.loje0611.tennisdoc.core.model.CoachTone.ENCOURAGING

                val contextBuilder = io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContextBuilder()
                val context = contextBuilder.buildContext(
                    sessionId = currentSessionId,
                    drillType = uiState.value.selectedDrill,
                    swings = swings,
                    baseline = null, // TODO: Fetch baseline if needed
                    durationSeconds = uiState.value.sessionDurationSeconds
                )
                
                val report = aiCoachService.createReport(context, provider = provider, apiKey = apiKey, tone = resolvedTone)
                
                val reportJson = """
                    {
                        "reportId": "${report.reportId}",
                        "sessionId": "${report.sessionId}",
                        "overallSummary": "${report.overallSummary.replace("\"", "\\\"").replace("\n", "\\n")}",
                        "isFallbackReport": ${report.isFallbackReport}
                    }
                """.trimIndent()
                
                swingHistoryRepository.saveAiCoachReport(currentSessionId, reportJson, System.currentTimeMillis())
                
                _aiCoachReport.value = report
            } catch (e: Exception) {
                // Ignore or handle
            } finally {
                _isGeneratingAiReport.value = false
            }
        }
    }

    fun resetPipeline() {
        pipeline.reset()
    }
}
