package io.github.loje0611.tennisdoc

import androidx.lifecycle.ViewModel
import io.github.loje0611.tennisdoc.service.SwingAnalysisForegroundService
import io.github.loje0611.tennisdoc.session.SwingAnalysisSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    val connectionState: StateFlow<BleConnectionState> =
        SwingAnalysisSessionState.connectionState

    val detectedSwingLabel: StateFlow<String> =
        SwingAnalysisSessionState.detectedSwingLabel

    val swingCount: StateFlow<Int> =
        SwingAnalysisSessionState.swingCount

    val sessionDurationSeconds: StateFlow<Long> =
        SwingAnalysisSessionState.sessionDurationSeconds

    private var clickCounter: Int = 0

    val isDebugModeEnabled: StateFlow<Boolean> = SwingAnalysisSessionState.debugModeEnabled

    fun scanAndConnect() {
        SwingAnalysisForegroundService.start(appContext)
    }

    fun disconnect() {
        SwingAnalysisForegroundService.requestStop(appContext)
    }

    fun onDebugActivationAreaTap() {
        if (SwingAnalysisSessionState.debugModeEnabled.value) return
        clickCounter++
        if (clickCounter >= 10) {
            SwingAnalysisSessionState.setDebugMode(true)
            clickCounter = 0
        }
    }

    fun simulateSwing(type: String) {
        if (!SwingAnalysisSessionState.debugModeEnabled.value) return
        if (SwingAnalysisSessionState.isPipelineRunning()) {
            SwingAnalysisForegroundService.requestDebugSimulation(appContext, type)
        } else {
            SwingAnalysisSessionState.updateSwingLabel(type)
        }
    }

    fun sendBleCommand(cmd: String) {
        SwingAnalysisForegroundService.requestSendBleCommand(appContext, cmd)
    }
}
