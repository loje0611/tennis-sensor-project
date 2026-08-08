package io.github.loje0611.tennisdoc.feature.match

import androidx.lifecycle.ViewModel
import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MatchViewModel @Inject constructor(
    private val port: MatchSessionPort,
) : ViewModel() {

    val connectionState: StateFlow<BleConnectionState> =
        port.connectionState

    val detectedSwingLabel: StateFlow<String> =
        port.detectedSwingLabel

    val swingCount: StateFlow<Int> =
        port.swingCount

    val sessionDurationSeconds: StateFlow<Long> =
        port.sessionDurationSeconds

    val isDebugModeEnabled: StateFlow<Boolean> = port.isDebugModeEnabled

    fun scanAndConnect() {
        port.scanAndConnect()
    }

    fun disconnect() {
        port.disconnect()
    }

    fun onDebugActivationAreaTap() {
        port.onDebugActivationAreaTap()
    }

    fun simulateSwing(type: String) {
        port.simulateSwing(type)
    }

    // sendBleCommand is unused by PracticeScreen according to the spec, but we can keep or remove it.
    // The spec says: "sendBleCommand는 PracticeScreen이 쓰지 않는다." so removing it is safe.
}
