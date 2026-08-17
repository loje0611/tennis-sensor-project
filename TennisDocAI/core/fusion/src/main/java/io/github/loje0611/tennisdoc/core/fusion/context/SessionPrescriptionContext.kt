package io.github.loje0611.tennisdoc.core.fusion.context

import io.github.loje0611.tennisdoc.core.fusion.anomaly.PersonalBaseline
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.model.DrillType

data class StageDelaysSummary(
    val hipToShoulderMs: Long,
    val shoulderToWristMs: Long,
    val wristToRacketMs: Long,
    val racketToImpactMs: Long
)

data class RepresentativeFlawSwing(
    val swingId: String,
    val faceState: RacketFaceState,
    val deviationDeg: Float,
    val energyEfficiency: Float,
    val stageDelaysMs: StageDelaysSummary,
    val diagnosisTags: List<String>
)

data class SessionPrescriptionContext(
    val sessionId: String,
    val drillType: DrillType,
    val totalSwingCount: Int,
    val durationSeconds: Long,
    val sequentialChainRatePercent: Int,
    val averageEnergyEfficiency: Float,
    val maxEnergyEfficiency: Float,
    val averageChainDurationMs: Long,
    val stageDelaysMs: StageDelaysSummary,
    val squareFaceRatePercent: Int,
    val openFaceRatePercent: Int,
    val closedFaceRatePercent: Int,
    val averageFaceDeviationDeg: Float,
    val averageRacketSpeed: Float,
    val flawTagCounts: Map<String, Int>,
    val primaryFlawTag: String?,
    val representativeFlaws: List<RepresentativeFlawSwing>,
    val isFatigued: Boolean = false,
    val fatigueScore: Float = 0f,
    val baselineAnomalyCount: Int = 0
) {
    fun toJsonString(): String {
        val buildStr = StringBuilder()
        buildStr.append("{")
        buildStr.append("\"sessionId\":\"").append(sessionId).append("\",")
        buildStr.append("\"drillType\":\"").append(drillType.name).append("\",")
        buildStr.append("\"totalSwingCount\":").append(totalSwingCount).append(",")
        buildStr.append("\"durationSeconds\":").append(durationSeconds).append(",")
        buildStr.append("\"sequentialChainRatePercent\":").append(sequentialChainRatePercent).append(",")
        
        buildStr.append("\"averageEnergyEfficiency\":").append(String.format(java.util.Locale.US, "%.2f", averageEnergyEfficiency)).append(",")
        buildStr.append("\"maxEnergyEfficiency\":").append(String.format(java.util.Locale.US, "%.2f", maxEnergyEfficiency)).append(",")
        buildStr.append("\"averageChainDurationMs\":").append(averageChainDurationMs).append(",")
        
        buildStr.append("\"stageDelaysMs\":{")
        buildStr.append("\"hipToShoulderMs\":").append(stageDelaysMs.hipToShoulderMs).append(",")
        buildStr.append("\"shoulderToWristMs\":").append(stageDelaysMs.shoulderToWristMs).append(",")
        buildStr.append("\"wristToRacketMs\":").append(stageDelaysMs.wristToRacketMs).append(",")
        buildStr.append("\"racketToImpactMs\":").append(stageDelaysMs.racketToImpactMs)
        buildStr.append("},")
        
        buildStr.append("\"squareFaceRatePercent\":").append(squareFaceRatePercent).append(",")
        buildStr.append("\"openFaceRatePercent\":").append(openFaceRatePercent).append(",")
        buildStr.append("\"closedFaceRatePercent\":").append(closedFaceRatePercent).append(",")
        buildStr.append("\"averageFaceDeviationDeg\":").append(String.format(java.util.Locale.US, "%.2f", averageFaceDeviationDeg)).append(",")
        buildStr.append("\"averageRacketSpeed\":").append(String.format(java.util.Locale.US, "%.2f", averageRacketSpeed)).append(",")
        
        buildStr.append("\"flawTagCounts\":{")
        val flawEntries = flawTagCounts.entries.toList()
        for (i in flawEntries.indices) {
            val entry = flawEntries[i]
            buildStr.append("\"").append(entry.key).append("\":").append(entry.value)
            if (i < flawEntries.size - 1) buildStr.append(",")
        }
        buildStr.append("},")
        
        if (primaryFlawTag == null) {
            buildStr.append("\"primaryFlawTag\":null,")
        } else {
            buildStr.append("\"primaryFlawTag\":\"").append(primaryFlawTag).append("\",")
        }
        
        buildStr.append("\"representativeFlaws\":[")
        for (i in representativeFlaws.indices) {
            val flaw = representativeFlaws[i]
            buildStr.append("{")
            buildStr.append("\"swingId\":\"").append(flaw.swingId).append("\",")
            buildStr.append("\"faceState\":\"").append(flaw.faceState.name).append("\",")
            buildStr.append("\"deviationDeg\":").append(String.format(java.util.Locale.US, "%.2f", flaw.deviationDeg)).append(",")
            buildStr.append("\"energyEfficiency\":").append(String.format(java.util.Locale.US, "%.2f", flaw.energyEfficiency)).append(",")
            
            buildStr.append("\"stageDelaysMs\":{")
            buildStr.append("\"hipToShoulderMs\":").append(flaw.stageDelaysMs.hipToShoulderMs).append(",")
            buildStr.append("\"shoulderToWristMs\":").append(flaw.stageDelaysMs.shoulderToWristMs).append(",")
            buildStr.append("\"wristToRacketMs\":").append(flaw.stageDelaysMs.wristToRacketMs).append(",")
            buildStr.append("\"racketToImpactMs\":").append(flaw.stageDelaysMs.racketToImpactMs)
            buildStr.append("},")
            
            buildStr.append("\"diagnosisTags\":[")
            for (j in flaw.diagnosisTags.indices) {
                buildStr.append("\"").append(flaw.diagnosisTags[j]).append("\"")
                if (j < flaw.diagnosisTags.size - 1) buildStr.append(",")
            }
            buildStr.append("]")
            
            buildStr.append("}")
            if (i < representativeFlaws.size - 1) buildStr.append(",")
        }
        buildStr.append("],")
        
        buildStr.append("\"isFatigued\":").append(isFatigued).append(",")
        buildStr.append("\"fatigueScore\":").append(String.format(java.util.Locale.US, "%.2f", fatigueScore)).append(",")
        buildStr.append("\"baselineAnomalyCount\":").append(baselineAnomalyCount)
        
        buildStr.append("}")
        return buildStr.toString()
    }
}
