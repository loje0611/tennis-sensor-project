package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.ui.geometry.Rect
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

data class ReplayTooltip(
    val targetJointIndex: Int,
    val jointX: Float,
    val jointY: Float,
    val text: String
)

data class TooltipBoxRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val tooltip: ReplayTooltip
) {
    fun toRect(): Rect = Rect(left, top, right, bottom)
}

data class LabReplayUiState(
    val fusedSwing: FusedSwing? = null,
    val durationMs: Long = 0L,
    val currentTimestampMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val currentPoseFrame: PoseFrame? = null,
    val currentImuPoint: ImuDataPoint? = null,
    val isImpactFrame: Boolean = false,
    val impactTimestampMs: Long = 0L,
    val tooltips: List<ReplayTooltip> = emptyList()
)
