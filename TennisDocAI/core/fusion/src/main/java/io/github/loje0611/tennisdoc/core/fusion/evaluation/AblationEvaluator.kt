package io.github.loje0611.tennisdoc.core.fusion.evaluation

import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngine
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

data class AblationScore(
    val tagJaccardDistance: Float,
    val isJaccardCriteriaMet: Boolean,
    val hasCausalExplanation: Boolean,
    val kineticChainStageGain: Int,
    val overallPass: Boolean
)

data class AblationTestCase(
    val testCaseId: String,
    val drillType: DrillType,
    val poses: List<PoseFrame>,
    val imuSamples: List<ImuDataPoint>,
    val visionOnlyTags: List<String>,
    val visionOnlyFeedback: String
)

data class AblationDatasetReport(
    val totalCases: Int,
    val passedCases: Int,
    val passRate: Float,
    val averageJaccardDistance: Float,
    val summary: String
)

class AblationEvaluator(
    private val jaccardThreshold: Float = 0.3f
) {

    private val causalKeywords = listOf(
        "원인", "때문에", "밀려", "열려", "닫혀", "지연", "페이스", "타이밍", "에너지", "완벽", "순차", "스퀘어", "동기화", "실패", "시간차"
    )

    fun evaluate(
        fusedSwing: FusedSwing,
        visionOnlyTags: List<String>,
        visionOnlyFeedback: String
    ): AblationScore {
        val diagnosis = fusedSwing.diagnosis
        val fusionTags = diagnosis?.diagnosisTags?.toSet() ?: emptySet()
        val visionTags = visionOnlyTags.toSet()

        val union = fusionTags + visionTags
        val intersection = fusionTags.intersect(visionTags)

        val dj = if (union.isEmpty()) {
            0.0f
        } else {
            1.0f - (intersection.size.toFloat() / union.size.toFloat())
        }

        val isJaccardMet = dj >= jaccardThreshold

        val explanation = diagnosis?.causalExplanation ?: ""
        val feedback = diagnosis?.coachingFeedback ?: ""
        val hasCausal = explanation.isNotBlank() && causalKeywords.any {
            explanation.contains(it) || feedback.contains(it)
        }

        val stages = fusedSwing.kineticChain.stages
        val hasRacket = stages.any { it.stage == KineticStageType.RACKET }
        val hasImpact = stages.any { it.stage == KineticStageType.IMPACT }
        val kineticGain = if (stages.size == 5 && hasRacket && hasImpact) 2 else 0

        val overallPass = isJaccardMet && hasCausal && (kineticGain >= 2 || !fusedSwing.anchor.isSynchronized)

        return AblationScore(
            tagJaccardDistance = dj,
            isJaccardCriteriaMet = isJaccardMet,
            hasCausalExplanation = hasCausal,
            kineticChainStageGain = kineticGain,
            overallPass = overallPass
        )
    }

    fun evaluateDataset(
        dataset: List<AblationTestCase>,
        engine: FusionEngine
    ): AblationDatasetReport {
        if (dataset.isEmpty()) {
            return AblationDatasetReport(
                totalCases = 0,
                passedCases = 0,
                passRate = 0.0f,
                averageJaccardDistance = 0.0f,
                summary = "Dataset is empty."
            )
        }

        val scores = dataset.map { testCase ->
            val fused = engine.fuse(testCase.drillType, testCase.poses, testCase.imuSamples)
            evaluate(fused, testCase.visionOnlyTags, testCase.visionOnlyFeedback)
        }

        val total = scores.size
        val passed = scores.count { it.overallPass }
        val passRate = passed.toFloat() / total.toFloat()
        val avgDj = scores.map { it.tagJaccardDistance }.average().toFloat()

        val summary = "Evaluated $total test cases: $passed passed (${String.format("%.1f", passRate * 100)}%), Average Jaccard Distance: ${String.format("%.3f", avgDj)}"

        return AblationDatasetReport(
            totalCases = total,
            passedCases = passed,
            passRate = passRate,
            averageJaccardDistance = avgDj,
            summary = summary
        )
    }
}
