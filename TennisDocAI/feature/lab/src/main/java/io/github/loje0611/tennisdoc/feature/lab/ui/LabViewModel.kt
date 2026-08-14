package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport
import io.github.loje0611.tennisdoc.core.fusion.anomaly.PersonalBaseline
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.feature.lab.pipeline.LabFusionPipeline
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LabViewModel(
    val pipeline: LabFusionPipeline
) : ViewModel() {

    val latestFusedSwing: StateFlow<FusedSwing?> = pipeline.latestFusedSwing
    val latestAnomalyReport: StateFlow<BaselineComparisonReport?> = pipeline.latestAnomalyReport
    val currentBaseline: StateFlow<PersonalBaseline?> = pipeline.currentBaseline

    fun onPoseDetected(frame: PoseFrame) {
        pipeline.feedPoseFrame(frame)
    }

    fun onImuReceived(sample: ImuDataPoint) {
        pipeline.feedImuSample(sample)
    }

    fun triggerSwing(sessionId: String, drillType: DrillType) {
        viewModelScope.launch {
            pipeline.onSwingTriggered(sessionId, drillType)
        }
    }

    fun resetPipeline() {
        pipeline.reset()
    }
}
