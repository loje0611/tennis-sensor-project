package io.github.loje0611.tennisdoc.feature.match

import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MatchViewModel 단위 테스트용 Fake 포트. 호출을 기록하고 상태를 직접 조작한다.
 */
class FakeMatchSessionPort : MatchSessionPort {

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _detectedSwingLabel = MutableStateFlow("")
    override val detectedSwingLabel: StateFlow<String> = _detectedSwingLabel.asStateFlow()

    private val _swingCount = MutableStateFlow(0)
    override val swingCount: StateFlow<Int> = _swingCount.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    override val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    private val _isDebugModeEnabled = MutableStateFlow(false)
    override val isDebugModeEnabled: StateFlow<Boolean> = _isDebugModeEnabled.asStateFlow()

    var scanAndConnectCalls: Int = 0
        private set
    var disconnectCalls: Int = 0
        private set
    var debugActivationTapCalls: Int = 0
        private set
    val simulatedSwingTypes: MutableList<String> = mutableListOf()

    fun setConnectionState(state: BleConnectionState) {
        _connectionState.value = state
    }

    fun setDetectedSwingLabel(label: String) {
        _detectedSwingLabel.value = label
    }

    fun setSwingCount(count: Int) {
        _swingCount.value = count
    }

    fun setSessionDurationSeconds(seconds: Long) {
        _sessionDurationSeconds.value = seconds
    }

    fun setDebugModeEnabled(enabled: Boolean) {
        _isDebugModeEnabled.value = enabled
    }

    override fun scanAndConnect() {
        scanAndConnectCalls++
    }

    override fun disconnect() {
        disconnectCalls++
    }

    override fun onDebugActivationAreaTap() {
        debugActivationTapCalls++
    }

    override fun simulateSwing(type: String) {
        simulatedSwingTypes += type
    }
}
