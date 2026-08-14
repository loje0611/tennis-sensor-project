package io.github.loje0611.tennisdoc.session

import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import io.github.loje0611.tennisdoc.feature.lab.session.LabSessionPort
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LabSessionPortImpl @Inject constructor() : LabSessionPort {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override val isSessionActive: StateFlow<Boolean> = SwingAnalysisSessionState.isSessionActive
    override val activeSessionId: StateFlow<String?> = SwingAnalysisSessionState.activeSessionId
    override val sessionDurationSeconds: StateFlow<Long> = SwingAnalysisSessionState.sessionDurationSeconds
    override val swingCount: StateFlow<Int> = SwingAnalysisSessionState.swingCount
    override val isSensorConnected: StateFlow<Boolean> = SwingAnalysisSessionState.connectionState
        .map { it is BleConnectionState.Connected }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SwingAnalysisSessionState.connectionState.value is BleConnectionState.Connected
        )

    override fun startSession(type: SessionType, drillType: DrillType): String {
        return SwingAnalysisSessionState.startSession(type, drillType)
    }

    override fun finishSession() {
        SwingAnalysisSessionState.finishSession()
    }
}
