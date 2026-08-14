package io.github.loje0611.tennisdoc.session

import android.util.Log
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import io.github.loje0611.tennisdoc.core.model.SwingClassificationKeys
import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 포그라운드 서비스와 UI(ViewModel)가 동일한 BLE·스윙 상태 및 명시적 세션 라이프사이클을 공유하기 위한 싱글톤 브리지.
 */
object SwingAnalysisSessionState {

    @Volatile
    var historyRepository: SwingHistoryRepository? = null

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var durationJob: Job? = null

    // ── 세션 라이프사이클 상태 (FR-2) ──────────────────────────────────
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _activeSessionType = MutableStateFlow<SessionType?>(null)
    val activeSessionType: StateFlow<SessionType?> = _activeSessionType.asStateFlow()

    private val _activeDrillType = MutableStateFlow<DrillType?>(null)
    val activeDrillType: StateFlow<DrillType?> = _activeDrillType.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    // ── BLE 및 센서 파이프라인 상태 ────────────────────────────────────
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _detectedSwingLabel = MutableStateFlow("")
    val detectedSwingLabel: StateFlow<String> = _detectedSwingLabel.asStateFlow()

    private val _pipelineRunning = MutableStateFlow(false)
    val pipelineRunning: StateFlow<Boolean> = _pipelineRunning.asStateFlow()

    private val _swingCount = MutableStateFlow(0)
    val swingCount: StateFlow<Int> = _swingCount.asStateFlow()

    private val _swingBreakdown = MutableStateFlow<Map<String, Int>>(emptyMap())
    val swingBreakdown: StateFlow<Map<String, Int>> = _swingBreakdown.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    @Volatile
    var sessionStartTimeMillis: Long = 0L
        private set

    private val _debugModeEnabled = MutableStateFlow(false)
    val debugModeEnabled: StateFlow<Boolean> = _debugModeEnabled.asStateFlow()

    @Volatile
    private var _mockModeActive: Boolean = false

    private val _lastRawSwingData = MutableStateFlow("")
    val lastRawSwingData: StateFlow<String> = _lastRawSwingData.asStateFlow()

    // ── 명시적 세션 제어 API (FR-2) ───────────────────────────────────

    fun startSession(type: SessionType, drillType: DrillType? = null): String {
        val sid = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        sessionStartTimeMillis = startTime

        _activeSessionId.value = sid
        _activeSessionType.value = type
        _activeDrillType.value = drillType
        _isSessionActive.value = true
        _swingCount.value = 0
        _swingBreakdown.value = emptyMap()
        _sessionDurationSeconds.value = 0L

        startDurationJob()

        val repo = historyRepository
        if (repo != null) {
            sessionScope.launch {
                try {
                    val provisional = SwingSessionEntity(
                        sessionId = sid,
                        sessionName = SwingSessionEntity.formatSessionName(startTime),
                        startTime = startTime,
                        sessionType = type.name,
                        drillType = drillType?.name,
                    )
                    repo.insertProvisionalSession(provisional)
                } catch (e: Exception) {
                    Log.w("SwingSessionState", "Failed to insert provisional session", e)
                }
            }
        }
        return sid
    }

    fun finishSession() {
        val sid = _activeSessionId.value ?: return
        val totalSwings = _swingCount.value
        val durationSecs = _sessionDurationSeconds.value
        val startTime = sessionStartTimeMillis
        val breakdownMap = _swingBreakdown.value

        stopDurationJob()
        _isSessionActive.value = false
        _activeSessionId.value = null
        _activeSessionType.value = null
        _activeDrillType.value = null

        val repo = historyRepository
        if (repo != null) {
            sessionScope.launch {
                withContext(NonCancellable) {
                    if (totalSwings > 0 && durationSecs > 0 && startTime > 0) {
                        val endTime = System.currentTimeMillis()
                        val breakdownNormalized = breakdownMap.mapKeys { SwingClassificationKeys.normalize(it.key) }
                        val fhVolley = breakdownNormalized["forehand volley"] ?: 0
                        val bhVolley = breakdownNormalized["backhand volley"] ?: 0
                        try {
                            repo.finalizeSession(
                                sessionId = sid,
                                endTime = endTime,
                                totalSwingCount = totalSwings,
                                durationMillis = durationSecs * 1000L,
                                fhVolley = fhVolley,
                                bhVolley = bhVolley,
                                breakdownNormalized = breakdownNormalized,
                            )
                        } catch (e: Exception) {
                            Log.w("SwingSessionState", "Failed to finalize session", e)
                        }
                    } else {
                        try {
                            repo.deleteSession(sid)
                        } catch (e: Exception) {
                            Log.w("SwingSessionState", "Failed to delete empty session", e)
                        }
                    }
                }
            }
        }
    }

    fun cancelSession() {
        val sid = _activeSessionId.value
        stopDurationJob()
        _isSessionActive.value = false
        _activeSessionId.value = null
        _activeSessionType.value = null
        _activeDrillType.value = null
        _swingCount.value = 0
        _swingBreakdown.value = emptyMap()
        _sessionDurationSeconds.value = 0L
        sessionStartTimeMillis = 0L

        if (sid != null) {
            val repo = historyRepository
            if (repo != null) {
                sessionScope.launch {
                    withContext(NonCancellable) {
                        try {
                            repo.deleteSession(sid)
                        } catch (e: Exception) {
                            Log.w("SwingSessionState", "Failed to delete canceled session", e)
                        }
                    }
                }
            }
        }
    }

    private fun startDurationJob() {
        durationJob?.cancel()
        durationJob = sessionScope.launch(Dispatchers.Default) {
            var seconds = 0L
            while (_isSessionActive.value) {
                _sessionDurationSeconds.value = seconds
                kotlinx.coroutines.delay(1000L)
                seconds++
            }
        }
    }

    private fun stopDurationJob() {
        durationJob?.cancel()
        durationJob = null
    }

    fun setDebugMode(enabled: Boolean) {
        _debugModeEnabled.value = enabled
        if (!enabled) {
            clickCounter = 0
        }
    }

    private var clickCounter: Int = 0

    /** 연속 탭으로 디버그 모드를 켤 때 필요한 횟수 (Settings·Match 공유). */
    const val DEBUG_ACTIVATION_TAP_THRESHOLD: Int = 10

    fun onDebugActivationAreaTap() {
        if (_debugModeEnabled.value) return
        clickCounter++
        if (clickCounter >= DEBUG_ACTIVATION_TAP_THRESHOLD) {
            setDebugMode(true)
            clickCounter = 0
        }
    }

    fun setMockMode(active: Boolean) {
        _mockModeActive = active
    }

    fun updateLastRawSwingData(data: String) {
        _lastRawSwingData.value = data
    }

    fun updateConnection(state: BleConnectionState) {
        _connectionState.value = state
    }

    fun setPipelineRunning(running: Boolean) {
        _pipelineRunning.value = running
    }

    fun isPipelineRunning(): Boolean = _pipelineRunning.value

    fun updateSwingLabel(label: String) {
        _detectedSwingLabel.value = label
    }

    /** CAS 기반 원자적 카운트 증가 — 동시 호출에도 안전. */
    fun incrementSwingCount(type: String) {
        val key = SwingClassificationKeys.normalize(type)
        _swingCount.update { it + 1 }
        _swingBreakdown.update { map ->
            map + (key to ((map[key] ?: 0) + 1))
        }
    }

    fun updateSessionDuration(seconds: Long) {
        _sessionDurationSeconds.value = seconds
    }

    fun updateSessionStartTime(timeMillis: Long) {
        sessionStartTimeMillis = timeMillis
    }

    fun resetSessionUiState() {
        stopDurationJob()
        _isSessionActive.value = false
        _activeSessionId.value = null
        _activeSessionType.value = null
        _activeDrillType.value = null
        _connectionState.value = BleConnectionState.Disconnected
        _detectedSwingLabel.value = ""
        _pipelineRunning.value = false
        _swingCount.value = 0
        _swingBreakdown.value = emptyMap()
        _sessionDurationSeconds.value = 0L
        sessionStartTimeMillis = 0L
        _lastRawSwingData.value = ""
        _mockModeActive = false
        setDebugMode(false)
    }

    private val _calibrationDoneChannel = Channel<Unit>(Channel.CONFLATED)

    fun triggerCalibrationDone() {
        _calibrationDoneChannel.trySend(Unit)
    }

    suspend fun waitForCalibrationDone() {
        _calibrationDoneChannel.receive()
    }

    fun clearCalibrationDone() {
        _calibrationDoneChannel.tryReceive()
    }

    private val _sensorReadyChannel = Channel<Unit>(Channel.CONFLATED)

    /** 서비스에서 첫 IMU 패킷 수신 시 호출. */
    fun triggerSensorReady() {
        _sensorReadyChannel.trySend(Unit)
    }

    /** 센서 데이터 수신이 시작될 때까지 대기. */
    suspend fun waitForSensorReady() {
        _sensorReadyChannel.receive()
    }

    fun clearSensorReady() {
        _sensorReadyChannel.tryReceive()
    }
}
