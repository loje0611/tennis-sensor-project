package io.github.loje0611.tennisdoc.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.loje0611.tennisdoc.core.data.repository.CalibrationConfig
import io.github.loje0611.tennisdoc.core.data.repository.CalibrationStore
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import io.github.loje0611.tennisdoc.data.CsvFileExporter
import io.github.loje0611.tennisdoc.service.SwingAnalysisForegroundService
import io.github.loje0611.tennisdoc.session.SwingAnalysisSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeveloperSettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val store: CalibrationStore,
    private val repository: SwingHistoryRepository,
    private val csvFileExporter: CsvFileExporter,
) : ViewModel() {

    val config: StateFlow<CalibrationConfig> = store.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalibrationConfig())

    val lastRawSwingData: StateFlow<String> = SwingAnalysisSessionState.lastRawSwingData

    val pipelineRunning: StateFlow<Boolean> = SwingAnalysisSessionState.pipelineRunning

    // ── Mock BLE ─────────────────────────────────────────────────────────

    private val _mockConnected = MutableStateFlow(false)
    val mockConnected: StateFlow<Boolean> = _mockConnected.asStateFlow()

    fun toggleMockConnection(enabled: Boolean) {
        if (enabled) {
            if (SwingAnalysisSessionState.isPipelineRunning()) return
            _mockConnected.value = true
            SwingAnalysisForegroundService.startMock(appContext)
        } else {
            _mockConnected.value = false
            SwingAnalysisForegroundService.stopMock(appContext)
        }
    }

    fun triggerMockSwing(swingType: String) {
        if (!_mockConnected.value) return
        SwingAnalysisForegroundService.triggerMockSwing(appContext, swingType)
    }

    override fun onCleared() {
        super.onCleared()
        if (_mockConnected.value) {
            _mockConnected.value = false
            SwingAnalysisForegroundService.stopMock(appContext)
        }
    }

    // ── Calibration ─────────────────────────────────────────────────────

    fun updateVolleyAccelThreshold(value: Float) {
        viewModelScope.launch { store.updateVolleyAccelThreshold(value) }
    }

    fun updateVolleyMaxDurationMs(value: Int) {
        viewModelScope.launch { store.updateVolleyMaxDurationMs(value) }
    }

    fun updateGyroFollowThroughThreshold(value: Float) {
        viewModelScope.launch { store.updateGyroFollowThroughThreshold(value) }
    }

    fun updatePowerMaxNormalization(value: Float) {
        viewModelScope.launch { store.updatePowerMaxNormalization(value) }
    }

    fun updateSpinMaxNormalization(value: Float) {
        viewModelScope.launch { store.updateSpinMaxNormalization(value) }
    }

    fun updateSmoothnessWorstVariance(value: Float) {
        viewModelScope.launch { store.updateSmoothnessWorstVariance(value) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { store.resetToDefaults() }
    }

    // ── CSV Export ───────────────────────────────────────────────────────

    sealed interface ExportResult {
        data class Success(val uri: Uri) : ExportResult
        data class Error(val message: String) : ExportResult
    }

    private val _exportEvent = MutableSharedFlow<ExportResult>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val exportEvent: SharedFlow<ExportResult> = _exportEvent

    fun exportCsv() {
        viewModelScope.launch {
            try {
                val uri = csvFileExporter.exportDataToCsv()
                _exportEvent.emit(ExportResult.Success(uri))
            } catch (e: Exception) {
                _exportEvent.emit(ExportResult.Error(e.message ?: "Export failed"))
            }
        }
    }
}
