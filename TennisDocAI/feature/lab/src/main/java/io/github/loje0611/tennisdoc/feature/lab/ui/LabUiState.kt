package io.github.loje0611.tennisdoc.feature.lab.ui

import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.model.DrillType

enum class CameraFacingMode {
    FRONT, // 전면 셀프 트레이닝 모드 (기본값)
    BACK   // 후면 코칭/관찰 모드
}

data class FarFieldHudState(
    val faceText: String,
    val faceColorHex: Long,
    val energyEfficiency: Float,
    val isSquare: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

data class SessionCompletionSummary(
    val sessionId: String,
    val drillName: String,
    val totalSwingCount: Int,
    val durationSeconds: Long,
    val squareRatePercent: Int,
    val averageEnergyEfficiency: Float,
    val latestRecordId: Long = 1L
)

data class LabUiState(
    val selectedDrill: DrillType = DrillType.FOREHAND,
    val isSessionActive: Boolean = false,
    val activeSessionId: String? = null,
    val sessionDurationSeconds: Long = 0L,
    val swingCount: Int = 0,
    val isSensorConnected: Boolean = false,
    val isSensorScanning: Boolean = false,
    val isDebugModeEnabled: Boolean = false,
    val cameraFacingMode: CameraFacingMode = CameraFacingMode.FRONT,
    val countdownSeconds: Int? = null,
    val farFieldHud: FarFieldHudState? = null,
    val completionSummary: SessionCompletionSummary? = null,
    val latestFusedSwing: FusedSwing? = null,
    val latestAnomalyReport: BaselineComparisonReport? = null,
    val isBodyFramed: Boolean = false
)
