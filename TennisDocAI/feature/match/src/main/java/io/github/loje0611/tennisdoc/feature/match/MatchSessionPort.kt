package io.github.loje0611.tennisdoc.feature.match

import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import kotlinx.coroutines.flow.StateFlow

interface MatchSessionPort {
    val connectionState: StateFlow<BleConnectionState>
    val detectedSwingLabel: StateFlow<String>
    val swingCount: StateFlow<Int>
    val sessionDurationSeconds: StateFlow<Long>
    val isDebugModeEnabled: StateFlow<Boolean>

    fun scanAndConnect()
    fun disconnect()
    fun onDebugActivationAreaTap()
    fun simulateSwing(type: String)
}
