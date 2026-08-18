package io.github.loje0611.tennisdoc.feature.history

import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing

data class LabSwingSummaryItem(
    val recordId: Long,
    val swingIndex: Int,
    val timestampMillis: Long,
    val faceState: String,
    val energyEfficiency: Float,
    val coachingFeedback: String,
    val fusedSwing: FusedSwing? = null,
    val hasVideo: Boolean = false
)

data class LabSessionDetailUiState(
    val session: SwingSessionEntity? = null,
    val swingItems: List<LabSwingSummaryItem> = emptyList(),
    val squareRatePercent: Int = 0,
    val averageEnergyEfficiency: Float = 0f,
    val isLoading: Boolean = false
)
