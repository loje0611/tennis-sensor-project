package io.github.loje0611.tennisdoc.core.model

data class LabRawSwingRecord(
    val id: Long = 0L,
    val sessionId: String,
    val drillType: DrillType,
    val timestampMillis: Long,
    val imuRawJson: String,
    val visionPosesJson: String,
    val impactOffsetMs: Long = 0L
)
