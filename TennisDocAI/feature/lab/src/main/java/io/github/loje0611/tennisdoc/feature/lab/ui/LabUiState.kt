package io.github.loje0611.tennisdoc.feature.lab.ui

import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.model.DrillType

data class LabUiState(
    val selectedDrill: DrillType = DrillType.FOREHAND_TOPSPIN,
    val isSessionActive: Boolean = false,
    val activeSessionId: String? = null,
    val sessionDurationSeconds: Long = 0L,
    val swingCount: Int = 0,
    val isSensorConnected: Boolean = false,
    val latestFusedSwing: FusedSwing? = null,
    val latestAnomalyReport: BaselineComparisonReport? = null
)
