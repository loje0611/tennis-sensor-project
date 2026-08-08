package io.github.loje0611.tennisdoc.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import io.github.loje0611.tennisdoc.feature.match.MatchSessionPort
import io.github.loje0611.tennisdoc.service.SwingAnalysisForegroundService
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchSessionPortImpl @Inject constructor(
    @ApplicationContext private val appContext: Context
) : MatchSessionPort {

    override val connectionState: StateFlow<BleConnectionState> = SwingAnalysisSessionState.connectionState
    override val detectedSwingLabel: StateFlow<String> = SwingAnalysisSessionState.detectedSwingLabel
    override val swingCount: StateFlow<Int> = SwingAnalysisSessionState.swingCount
    override val sessionDurationSeconds: StateFlow<Long> = SwingAnalysisSessionState.sessionDurationSeconds
    override val isDebugModeEnabled: StateFlow<Boolean> = SwingAnalysisSessionState.debugModeEnabled

    override fun scanAndConnect() {
        SwingAnalysisForegroundService.start(appContext)
    }

    override fun disconnect() {
        SwingAnalysisForegroundService.requestStop(appContext)
    }

    override fun onDebugActivationAreaTap() {
        SwingAnalysisSessionState.onDebugActivationAreaTap()
    }

    override fun simulateSwing(type: String) {
        if (!SwingAnalysisSessionState.debugModeEnabled.value) return
        if (SwingAnalysisSessionState.isPipelineRunning()) {
            SwingAnalysisForegroundService.requestDebugSimulation(appContext, type)
        } else {
            SwingAnalysisSessionState.updateSwingLabel(type)
        }
    }
}
