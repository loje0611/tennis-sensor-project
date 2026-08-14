package io.github.loje0611.tennisdoc.core.fusion.anomaly

import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.model.DrillType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class BaselineDistribution(
    val count: Int,
    val mean: Float,
    val variance: Float,
    val stdDev: Float
)

data class PersonalBaseline(
    val drillType: DrillType,
    val totalSwings: Int,
    val distributions: Map<String, BaselineDistribution>,
    val isReliable: Boolean = totalSwings >= 5
)

enum class AnomalySeverity {
    NORMAL, WARNING, CRITICAL
}

data class AnomalyResult(
    val metricKey: String,
    val currentValue: Float,
    val baselineMean: Float,
    val zScore: Float,
    val isAnomaly: Boolean,
    val severity: AnomalySeverity,
    val description: String
)

data class FatigueAnalysis(
    val fatigueScore: Float,
    val isFatigued: Boolean,
    val formBreakdownSummary: String?
)

data class BaselineComparisonReport(
    val drillType: DrillType,
    val anomalies: List<AnomalyResult>,
    val fatigue: FatigueAnalysis,
    val coachingRecommendation: String
)

class StatisticalAnomalyDetector(
    private val warningZThreshold: Float = 1.5f,
    private val criticalZThreshold: Float = 2.5f,
    private val epsilon: Float = 1e-4f
) {

    companion object {
        const val KEY_RACKET_SPEED = "racketSpeed"
        const val KEY_ENERGY_EFFICIENCY = "energyTransferEfficiency"
        const val KEY_FACE_DEVIATION = "faceDeviationDeg"
        const val KEY_WRIST_RACKET_DELAY = "wristRacketDelayMs"
        const val KEY_TOTAL_DURATION = "totalDurationMs"
    }

    fun extractMetrics(swing: FusedSwing): Map<String, Float> {
        val stages = swing.kineticChain.stages
        val racketStage = stages.firstOrNull { it.stage == KineticStageType.RACKET }
        val racketSpeed = racketStage?.peakValue ?: 0f
        val wristRacketDelay = (racketStage?.delayFromPreviousMs ?: 0L).toFloat()

        return mapOf(
            KEY_RACKET_SPEED to racketSpeed,
            KEY_ENERGY_EFFICIENCY to swing.kineticChain.energyTransferEfficiency,
            KEY_FACE_DEVIATION to swing.racketImpact.deviationDeg,
            KEY_WRIST_RACKET_DELAY to wristRacketDelay,
            KEY_TOTAL_DURATION to swing.kineticChain.totalDurationMs.toFloat()
        )
    }

    fun updateBaseline(existing: PersonalBaseline?, swing: FusedSwing): PersonalBaseline {
        val currentMetrics = extractMetrics(swing)
        val existingDist = existing?.distributions ?: emptyMap()
        val totalSwings = (existing?.totalSwings ?: 0) + 1

        val updatedDist = mutableMapOf<String, BaselineDistribution>()

        for ((key, value) in currentMetrics) {
            val prev = existingDist[key]
            if (prev == null || prev.count == 0) {
                updatedDist[key] = BaselineDistribution(
                    count = 1,
                    mean = value,
                    variance = 0f,
                    stdDev = 0f
                )
            } else {
                val k = prev.count + 1
                val oldMean = prev.mean
                val newMean = oldMean + (value - oldMean) / k
                val oldS = prev.variance * (prev.count - 1).coerceAtLeast(0)
                val newS = oldS + (value - oldMean) * (value - newMean)
                val newVar = if (k > 1) newS / (k - 1) else 0f
                val newStdDev = sqrt(max(0f, newVar))

                updatedDist[key] = BaselineDistribution(
                    count = k,
                    mean = newMean,
                    variance = newVar,
                    stdDev = newStdDev
                )
            }
        }

        return PersonalBaseline(
            drillType = swing.drillType,
            totalSwings = totalSwings,
            distributions = updatedDist,
            isReliable = totalSwings >= 5
        )
    }

    fun detectAnomalies(baseline: PersonalBaseline, swing: FusedSwing): BaselineComparisonReport {
        val currentMetrics = extractMetrics(swing)
        val anomalyResults = mutableListOf<AnomalyResult>()

        for ((key, value) in currentMetrics) {
            val dist = baseline.distributions[key]
            if (dist == null || !baseline.isReliable) {
                anomalyResults.add(
                    AnomalyResult(
                        metricKey = key,
                        currentValue = value,
                        baselineMean = dist?.mean ?: value,
                        zScore = 0f,
                        isAnomaly = false,
                        severity = AnomalySeverity.NORMAL,
                        description = "Baseline 축적 중 (${baseline.totalSwings}/5)"
                    )
                )
                continue
            }

            val std = max(dist.stdDev, epsilon)
            val z = (value - dist.mean) / std
            val absZ = abs(z)

            val severity = when {
                absZ >= criticalZThreshold -> AnomalySeverity.CRITICAL
                absZ >= warningZThreshold -> AnomalySeverity.WARNING
                else -> AnomalySeverity.NORMAL
            }
            val isAnomaly = severity != AnomalySeverity.NORMAL

            val description = formatAnomalyDescription(key, value, dist.mean, z, severity)

            anomalyResults.add(
                AnomalyResult(
                    metricKey = key,
                    currentValue = value,
                    baselineMean = dist.mean,
                    zScore = z,
                    isAnomaly = isAnomaly,
                    severity = severity,
                    description = description
                )
            )
        }

        val fatigue = analyzeFatigueTrend(listOf(swing), baseline)

        val criticalCount = anomalyResults.count { it.severity == AnomalySeverity.CRITICAL }
        val warningCount = anomalyResults.count { it.severity == AnomalySeverity.WARNING }

        val recommendation = when {
            fatigue.isFatigued -> "세션 피로 누적이 감지되었습니다. 휴식 후 스윙 템포를 재점검하세요."
            criticalCount > 0 -> "평소 폼 대비 주요 역학 지표의 큰 편차가 감지되었습니다. 기본기 폼을 점검하세요."
            warningCount > 0 -> "일부 역학 지표가 Baseline 기준 범위를 다소 벗어났습니다."
            else -> "평소 Baseline에 부합하는 안정적인 스윙입니다."
        }

        return BaselineComparisonReport(
            drillType = swing.drillType,
            anomalies = anomalyResults,
            fatigue = fatigue,
            coachingRecommendation = recommendation
        )
    }

    fun analyzeFatigueTrend(recentSwings: List<FusedSwing>, baseline: PersonalBaseline): FatigueAnalysis {
        if (recentSwings.isEmpty() || !baseline.isReliable) {
            return FatigueAnalysis(
                fatigueScore = 0f,
                isFatigued = false,
                formBreakdownSummary = null
            )
        }

        val speedDist = baseline.distributions[KEY_RACKET_SPEED]
        val effDist = baseline.distributions[KEY_ENERGY_EFFICIENCY]
        val delayDist = baseline.distributions[KEY_WRIST_RACKET_DELAY]

        var totalSpeedZ = 0f
        var totalEffZ = 0f
        var totalDelayZ = 0f
        val count = recentSwings.size

        for (s in recentSwings) {
            val m = extractMetrics(s)
            if (speedDist != null) {
                totalSpeedZ += (m[KEY_RACKET_SPEED]!! - speedDist.mean) / max(speedDist.stdDev, epsilon)
            }
            if (effDist != null) {
                totalEffZ += (m[KEY_ENERGY_EFFICIENCY]!! - effDist.mean) / max(effDist.stdDev, epsilon)
            }
            if (delayDist != null) {
                totalDelayZ += (m[KEY_WRIST_RACKET_DELAY]!! - delayDist.mean) / max(delayDist.stdDev, epsilon)
            }
        }

        val avgSpeedZ = totalSpeedZ / count
        val avgEffZ = totalEffZ / count
        val avgDelayZ = totalDelayZ / count

        val speedPenalty = (-avgSpeedZ).coerceAtLeast(0f) / 3.0f
        val effPenalty = (-avgEffZ).coerceAtLeast(0f) / 3.0f
        val delayPenalty = avgDelayZ.coerceAtLeast(0f) / 3.0f

        val fatigueScore = ((speedPenalty + effPenalty + delayPenalty) / 3.0f).coerceIn(0.0f, 1.0f)
        val isFatigued = fatigueScore >= 0.7f || (avgSpeedZ < -2.0f && avgEffZ < -1.5f)

        val summary = if (isFatigued) {
            "세션 후반 피로 누적으로 인해 라켓 스피드가 평소 대비 유의미하게 감소하고 체인 전달 지연이 발생하고 있습니다. 휴식을 권장합니다."
        } else {
            null
        }

        return FatigueAnalysis(
            fatigueScore = fatigueScore,
            isFatigued = isFatigued,
            formBreakdownSummary = summary
        )
    }

    private fun formatAnomalyDescription(
        key: String,
        current: Float,
        mean: Float,
        z: Float,
        severity: AnomalySeverity
    ): String {
        val zFormatted = String.format("%.1f", abs(z))
        val metricName = when (key) {
            KEY_RACKET_SPEED -> "라켓 헤드 스피드"
            KEY_ENERGY_EFFICIENCY -> "에너지 전달 효율"
            KEY_FACE_DEVIATION -> "라켓 페이스 편차"
            KEY_WRIST_RACKET_DELAY -> "손목-라켓 릴리즈 지연"
            KEY_TOTAL_DURATION -> "총 스윙 소요시간"
            else -> key
        }

        return when (severity) {
            AnomalySeverity.CRITICAL -> "평소보다 $metricName(${String.format("%.1f", current)})가 평균(${String.format("%.1f", mean)}) 대비 ${zFormatted}σ 매우 크게 벗어났습니다."
            AnomalySeverity.WARNING -> "평소보다 $metricName(${String.format("%.1f", current)})가 평균(${String.format("%.1f", mean)}) 대비 ${zFormatted}σ 다소 벗어났습니다."
            AnomalySeverity.NORMAL -> "${metricName}가 평소 기준선(평균 ${String.format("%.1f", mean)}) 범위 내에서 안정적입니다."
        }
    }
}
