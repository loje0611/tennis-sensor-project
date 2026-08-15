package io.github.loje0611.tennisdoc.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import io.github.loje0611.tennisdoc.feature.lab.session.LabSessionPort
import io.github.loje0611.tennisdoc.service.SwingAnalysisForegroundService
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LabSessionPortImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : LabSessionPort {

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
    override val isSensorScanning: StateFlow<Boolean> = SwingAnalysisSessionState.connectionState
        .map { it is BleConnectionState.Scanning }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SwingAnalysisSessionState.connectionState.value is BleConnectionState.Scanning
        )
    override val isDebugModeEnabled: StateFlow<Boolean> = SwingAnalysisSessionState.debugModeEnabled

    override fun startSession(type: SessionType, drillType: DrillType): String {
        return SwingAnalysisSessionState.startSession(type, drillType)
    }

    override fun finishSession() {
        SwingAnalysisSessionState.finishSession()
    }

    override fun connectSensor() {
        SwingAnalysisForegroundService.start(appContext)
    }

    override fun disconnectSensor() {
        SwingAnalysisForegroundService.requestStop(appContext)
    }
}
