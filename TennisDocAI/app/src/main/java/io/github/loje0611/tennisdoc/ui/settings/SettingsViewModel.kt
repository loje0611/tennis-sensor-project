package io.github.loje0611.tennisdoc.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.loje0611.tennisdoc.data.repository.ThemePreferencesRepository
import io.github.loje0611.tennisdoc.service.SwingAnalysisForegroundService
import io.github.loje0611.tennisdoc.session.SwingAnalysisSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

enum class CalibrationStep {
    IDLE,
    CONNECTING,
    CALIBRATING
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val themePreferences: ThemePreferencesRepository,
) : ViewModel() {

    val isDarkMode: StateFlow<Boolean> = themePreferences.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun toggleDarkMode() {
        viewModelScope.launch {
            themePreferences.setDarkMode(!isDarkMode.value)
        }
    }

    private val _calibrationStep = MutableStateFlow(CalibrationStep.IDLE)
    val calibrationStep: StateFlow<CalibrationStep> = _calibrationStep.asStateFlow()

    private val _calibrationResultEvent = MutableSharedFlow<Boolean>()
    val calibrationResultEvent: SharedFlow<Boolean> = _calibrationResultEvent

    private var calibrationJob: kotlinx.coroutines.Job? = null

    fun startAutoCalibration() {
        if (_calibrationStep.value != CalibrationStep.IDLE) return

        calibrationJob = viewModelScope.launch {
            var autoConnected = false

            try {
                if (SwingAnalysisSessionState.connectionState.value.isDisconnectedOrError) {
                    _calibrationStep.value = CalibrationStep.CONNECTING
                    SwingAnalysisForegroundService.start(appContext)
                    autoConnected = true

                    withTimeout(10000L) {
                        SwingAnalysisSessionState.connectionState.first { it.isConnected }
                    }

                    withTimeout(8000L) {
                        SwingAnalysisSessionState.waitForSensorReady()
                    }
                }

                if (!SwingAnalysisSessionState.connectionState.value.isConnected) {
                    _calibrationResultEvent.emit(false)
                    return@launch
                }

                _calibrationStep.value = CalibrationStep.CALIBRATING
                SwingAnalysisSessionState.clearCalibrationDone()
                SwingAnalysisForegroundService.requestSendBleCommand(appContext, "CAL")

                withTimeout(5000L) {
                    SwingAnalysisSessionState.waitForCalibrationDone()
                }

                _calibrationResultEvent.emit(true)

            } catch (e: TimeoutCancellationException) {
                _calibrationResultEvent.emit(false)
            } finally {
                _calibrationStep.value = CalibrationStep.IDLE
                if (autoConnected) {
                    SwingAnalysisForegroundService.requestStop(appContext)
                }
                calibrationJob = null
            }
        }
    }

    fun cancelCalibration() {
        calibrationJob?.cancel()
        calibrationJob = null
        _calibrationStep.value = CalibrationStep.IDLE
    }
}
