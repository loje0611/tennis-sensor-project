package io.github.loje0611.tennisdoc.core.fusion.model

import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import kotlin.math.abs

enum class KineticStageType {
    HIP, SHOULDER, WRIST, RACKET, IMPACT
}

data class KineticStage(
    val stage: KineticStageType,
    val peakTimestampMs: Long,
    val peakValue: Float,
    val delayFromPreviousMs: Long = 0L
)

data class KineticChain5Stage(
    val stages: List<KineticStage>,
    val isSequential: Boolean,
    val totalDurationMs: Long,
    val energyTransferEfficiency: Float
) {
    init {
        require(stages.size == 5) { "KineticChain5Stage must contain exactly 5 stages" }
    }
}

enum class RacketFaceState {
    OPEN, CLOSED, SQUARE
}

data class RacketImpactOrientation(
    val rollDeg: Float,
    val pitchDeg: Float,
    val yawDeg: Float,
    val faceState: RacketFaceState,
    val deviationDeg: Float
)

data class SyncAnchor(
    val visionImpactTimestampMs: Long,
    val sensorImpactTimestampMs: Long,
    val timeOffsetMs: Long = sensorImpactTimestampMs - visionImpactTimestampMs,
    val confidence: Float,
    val isSynchronized: Boolean = abs(timeOffsetMs) <= 100L
)

data class ImuDataPoint(
    val timestampMs: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float
)

data class FusionDiagnosis(
    val diagnosisTags: List<String>,
    val primaryCause: String,
    val coachingFeedback: String,
    val causalExplanation: String
)

data class FusedSwing(
    val swingId: String,
    val sessionId: String,
    val drillType: DrillType,
    val anchor: SyncAnchor,
    val kineticChain: KineticChain5Stage,
    val racketImpact: RacketImpactOrientation,
    val visionPoses: List<PoseFrame>,
    val imuSamples: List<ImuDataPoint>,
    val diagnosis: FusionDiagnosis? = null
)
