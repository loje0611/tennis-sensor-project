package io.github.loje0611.tennisdoc.core.fusion.context

import io.github.loje0611.tennisdoc.core.fusion.anomaly.PersonalBaseline
import io.github.loje0611.tennisdoc.core.fusion.anomaly.StatisticalAnomalyDetector
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.model.DrillType
import kotlin.math.roundToInt

private class ImmutableMapWrapper<K, V>(private val map: Map<K, V>) : Map<K, V> by map

class SessionPrescriptionContextBuilder {

    private val detector = StatisticalAnomalyDetector()

    fun buildContext(
        sessionId: String,
        drillType: DrillType,
        swings: List<FusedSwing>,
        baseline: PersonalBaseline? = null,
        durationSeconds: Long = 0L
    ): SessionPrescriptionContext {
        val totalSwingCount = swings.size
        
        if (totalSwingCount == 0) {
            return SessionPrescriptionContext(
                sessionId = sessionId,
                drillType = drillType,
                totalSwingCount = 0,
                durationSeconds = durationSeconds,
                sequentialChainRatePercent = 0,
                averageEnergyEfficiency = 0f,
                maxEnergyEfficiency = 0f,
                averageChainDurationMs = 0L,
                stageDelaysMs = StageDelaysSummary(0, 0, 0, 0),
                squareFaceRatePercent = 0,
                openFaceRatePercent = 0,
                closedFaceRatePercent = 0,
                averageFaceDeviationDeg = 0f,
                averageRacketSpeed = 0f,
                flawTagCounts = emptyMap(),
                primaryFlawTag = null,
                representativeFlaws = emptyList(),
                isFatigued = false,
                fatigueScore = 0f,
                baselineAnomalyCount = 0
            )
        }

        var seqCount = 0
        var sumEnergyEff = 0f
        var maxEnergyEff = 0f
        var sumChainDur = 0L
        
        var sumHipShoulder = 0L
        var sumShoulderWrist = 0L
        var sumWristRacket = 0L
        var sumRacketImpact = 0L

        var squareCount = 0
        var openCount = 0
        var closedCount = 0
        var sumFaceDev = 0f
        var sumRacketSpeed = 0f

        val flawCounts = mutableMapOf<String, Int>()
        val excludedTags = setOf("CLEAN_STRIKE", "OPTIMAL_CHAIN", "SQUARE_FACE")
        
        val flawSwings = mutableListOf<FusedSwing>()

        for (swing in swings) {
            val chain = swing.kineticChain
            if (chain.isSequential) seqCount++
            sumEnergyEff += chain.energyTransferEfficiency
            if (chain.energyTransferEfficiency > maxEnergyEff) {
                maxEnergyEff = chain.energyTransferEfficiency
            }
            sumChainDur += chain.totalDurationMs
            
            chain.stages.find { it.stage == KineticStageType.SHOULDER }?.let { sumHipShoulder += it.delayFromPreviousMs }
            chain.stages.find { it.stage == KineticStageType.WRIST }?.let { sumShoulderWrist += it.delayFromPreviousMs }
            chain.stages.find { it.stage == KineticStageType.RACKET }?.let { sumWristRacket += it.delayFromPreviousMs }
            chain.stages.find { it.stage == KineticStageType.IMPACT }?.let { sumRacketImpact += it.delayFromPreviousMs }

            val impact = swing.racketImpact
            when (impact.faceState) {
                RacketFaceState.SQUARE -> squareCount++
                RacketFaceState.OPEN -> openCount++
                RacketFaceState.CLOSED -> closedCount++
            }
            sumFaceDev += impact.deviationDeg
            
            val rSpeed = chain.stages.find { it.stage == KineticStageType.RACKET }?.peakValue ?: 0f
            sumRacketSpeed += rSpeed

            val diagTags = swing.diagnosis?.diagnosisTags ?: emptyList()
            val actualFlaws = diagTags.filter { it !in excludedTags }
            if (actualFlaws.isNotEmpty()) {
                flawSwings.add(swing)
                for (tag in actualFlaws) {
                    flawCounts[tag] = flawCounts.getOrDefault(tag, 0) + 1
                }
            }
        }

        val primaryFlawTag = flawCounts.maxByOrNull { it.value }?.key

        val repFlaws = flawSwings.sortedBy { it.kineticChain.energyTransferEfficiency }
            .take(2)
            .map { s ->
                val diagTags = s.diagnosis?.diagnosisTags?.filter { it !in excludedTags } ?: emptyList()
                val chain = s.kineticChain
                val hs = chain.stages.find { it.stage == KineticStageType.SHOULDER }?.delayFromPreviousMs ?: 0L
                val sw = chain.stages.find { it.stage == KineticStageType.WRIST }?.delayFromPreviousMs ?: 0L
                val wr = chain.stages.find { it.stage == KineticStageType.RACKET }?.delayFromPreviousMs ?: 0L
                val ri = chain.stages.find { it.stage == KineticStageType.IMPACT }?.delayFromPreviousMs ?: 0L
                
                RepresentativeFlawSwing(
                    swingId = s.swingId,
                    faceState = s.racketImpact.faceState,
                    deviationDeg = s.racketImpact.deviationDeg,
                    energyEfficiency = chain.energyTransferEfficiency,
                    stageDelaysMs = StageDelaysSummary(hs, sw, wr, ri),
                    diagnosisTags = diagTags
                )
            }

        var isFatigued = false
        var fatigueScore = 0f
        var totalAnomalies = 0

        if (baseline != null) {
            val fatigue = detector.analyzeFatigueTrend(swings, baseline)
            isFatigued = fatigue.isFatigued
            fatigueScore = fatigue.fatigueScore
            
            for (swing in swings) {
                val report = detector.detectAnomalies(baseline, swing)
                totalAnomalies += report.anomalies.count { it.isAnomaly }
            }
        }

        return SessionPrescriptionContext(
            sessionId = sessionId,
            drillType = drillType,
            totalSwingCount = totalSwingCount,
            durationSeconds = durationSeconds,
            sequentialChainRatePercent = (seqCount.toFloat() / totalSwingCount * 100).roundToInt(),
            averageEnergyEfficiency = sumEnergyEff / totalSwingCount,
            maxEnergyEfficiency = maxEnergyEff,
            averageChainDurationMs = sumChainDur / totalSwingCount,
            stageDelaysMs = StageDelaysSummary(
                hipToShoulderMs = sumHipShoulder / totalSwingCount,
                shoulderToWristMs = sumShoulderWrist / totalSwingCount,
                wristToRacketMs = sumWristRacket / totalSwingCount,
                racketToImpactMs = sumRacketImpact / totalSwingCount
            ),
            squareFaceRatePercent = (squareCount.toFloat() / totalSwingCount * 100).roundToInt(),
            openFaceRatePercent = (openCount.toFloat() / totalSwingCount * 100).roundToInt(),
            closedFaceRatePercent = (closedCount.toFloat() / totalSwingCount * 100).roundToInt(),
            averageFaceDeviationDeg = sumFaceDev / totalSwingCount,
            averageRacketSpeed = sumRacketSpeed / totalSwingCount,
            flawTagCounts = ImmutableMapWrapper(flawCounts.toMap()),
            primaryFlawTag = primaryFlawTag,
            representativeFlaws = repFlaws,
            isFatigued = isFatigued,
            fatigueScore = fatigueScore,
            baselineAnomalyCount = totalAnomalies
        )
    }
}
