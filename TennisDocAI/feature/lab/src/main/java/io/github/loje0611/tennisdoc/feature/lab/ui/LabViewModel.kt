package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.feature.lab.pipeline.LabFusionPipeline
import io.github.loje0611.tennisdoc.feature.lab.session.LabSessionPort
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LabViewModel @Inject constructor(
    val pipeline: LabFusionPipeline,
    val sessionPort: LabSessionPort? = null
) : ViewModel() {

    private val _selectedDrill = MutableStateFlow(DrillType.FOREHAND_TOPSPIN)
    val selectedDrill: StateFlow<DrillType> = _selectedDrill.asStateFlow()

    private val _localIsSessionActive = MutableStateFlow(false)
    private val _localSessionId = MutableStateFlow<String?>(null)
    private val _localSessionDuration = MutableStateFlow(0L)
    private val _localSwingCount = MutableStateFlow(0)
    private val _localSensorConnected = MutableStateFlow(false)

    val uiState: StateFlow<LabUiState> = combine(
        _selectedDrill,
        sessionPort?.isSessionActive ?: _localIsSessionActive,
        sessionPort?.activeSessionId ?: _localSessionId,
        sessionPort?.sessionDurationSeconds ?: _localSessionDuration,
        sessionPort?.swingCount ?: _localSwingCount,
        sessionPort?.isSensorConnected ?: _localSensorConnected,
        pipeline.latestFusedSwing,
        pipeline.latestAnomalyReport
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        LabUiState(
            selectedDrill = array[0] as DrillType,
            isSessionActive = array[1] as Boolean,
            activeSessionId = array[2] as String?,
            sessionDurationSeconds = array[3] as Long,
            swingCount = array[4] as Int,
            isSensorConnected = array[5] as Boolean,
            latestFusedSwing = array[6] as FusedSwing?,
            latestAnomalyReport = array[7] as BaselineComparisonReport?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LabUiState()
    )

    fun selectDrill(drillType: DrillType) {
        if (!uiState.value.isSessionActive) {
            _selectedDrill.value = drillType
        }
    }

    fun startSession() {
        val drill = _selectedDrill.value
        val port = sessionPort
        if (port != null) {
            port.startSession(SessionType.LAB, drill)
        } else {
            _localIsSessionActive.value = true
            _localSessionId.value = "local-session"
        }
    }

    fun finishSession() {
        val port = sessionPort
        if (port != null) {
            port.finishSession()
        } else {
            _localIsSessionActive.value = false
            _localSessionId.value = null
        }
        pipeline.reset()
    }

    fun onPoseDetected(frame: PoseFrame) {
        pipeline.feedPoseFrame(frame)
    }

    fun onImuReceived(sample: ImuDataPoint) {
        pipeline.feedImuSample(sample)
    }

    fun triggerSwing(sessionId: String? = null, drillType: DrillType? = null) {
        val targetSessionId = sessionId ?: uiState.value.activeSessionId ?: "temp-session"
        val targetDrill = drillType ?: _selectedDrill.value
        viewModelScope.launch {
            pipeline.onSwingTriggered(targetSessionId, targetDrill)
        }
    }

    fun resetPipeline() {
        pipeline.reset()
    }
}
