package io.github.loje0611.tennisdoc.session

import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import io.github.loje0611.tennisdoc.analysis.SwingClassificationKeys
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 포그라운드 서비스와 UI(ViewModel)가 동일한 BLE·스윙 상태를 구독하기 위한 앱 프로세스 내 싱글톤 브리지.
 */
object SwingAnalysisSessionState {

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

    /** 서비스 내부에서만 읽기/쓰기. UI에 Flow 노출하지 않음. */
    @Volatile
    var sessionStartTimeMillis: Long = 0L
        private set

    private val _debugModeEnabled = MutableStateFlow(false)
    val debugModeEnabled: StateFlow<Boolean> = _debugModeEnabled.asStateFlow()

    @Volatile
    private var _mockModeActive: Boolean = false

    private val _lastRawSwingData = MutableStateFlow("")
    val lastRawSwingData: StateFlow<String> = _lastRawSwingData.asStateFlow()

    fun setDebugMode(enabled: Boolean) {
        _debugModeEnabled.value = enabled
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
        _connectionState.value = BleConnectionState.Disconnected
        _detectedSwingLabel.value = ""
        _pipelineRunning.value = false
        _swingCount.value = 0
        _swingBreakdown.value = emptyMap()
        _sessionDurationSeconds.value = 0L
        sessionStartTimeMillis = 0L
        _lastRawSwingData.value = ""
        _mockModeActive = false
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
