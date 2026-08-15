package io.github.loje0611.tennisdoc.core.fusion.evaluation

import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngineImpl
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.FusionDiagnosis
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AblationEvaluatorTest {

    private val evaluator = AblationEvaluator(jaccardThreshold = 0.3f)
    private val fusionEngine = FusionEngineImpl()

    private fun createDummyFusedSwing(
        tags: List<String>,
        causalExplanation: String,
        isSync: Boolean = true
    ): FusedSwing {
        val anchor = SyncAnchor(1000L, 1000L, 0L, 0.95f, isSync)
        val stages = listOf(
            KineticStage(KineticStageType.HIP, 1000L, 10f),
            KineticStage(KineticStageType.SHOULDER, 1030L, 15f, 30L),
            KineticStage(KineticStageType.WRIST, 1060L, 20f, 30L),
            KineticStage(KineticStageType.RACKET, 1080L, 1500f, 20L),
            KineticStage(KineticStageType.IMPACT, 1100L, 25f, 20L)
        )
        val chain = KineticChain5Stage(stages, true, 100L, 95f)
        val orientation = RacketImpactOrientation(0f, 0f, 0f, RacketFaceState.SQUARE, 0f)
        val diagnosis = FusionDiagnosis(
            diagnosisTags = tags,
            primaryCause = "주요 원인",
            coachingFeedback = "코칭 피드백",
            causalExplanation = causalExplanation
        )
        return FusedSwing(
            swingId = "test-swing",
            sessionId = "test-session",
            drillType = DrillType.FOREHAND,
            anchor = anchor,
            kineticChain = chain,
            racketImpact = orientation,
            visionPoses = emptyList(),
            imuSamples = emptyList(),
            diagnosis = diagnosis
        )
    }

    @Test
    fun `AC-2 Jaccard distance calculation for distinct tagsets yields correct distance`() {
        // Fusion: ["FACE_OPEN", "EARLY_BODY_OPEN"], Vision: ["EARLY_BODY_OPEN"]
        // Union: 2, Intersection: 1 -> J = 1/2 = 0.5, DJ = 1 - 0.5 = 0.5
        val fused = createDummyFusedSwing(
            tags = listOf("FACE_OPEN", "EARLY_BODY_OPEN"),
            causalExplanation = "상체가 골반보다 일찍 열려 임팩트 타점이 뒤로 밀렸기 때문에 페이스가 열렸습니다."
        )

        val score = evaluator.evaluate(
            fusedSwing = fused,
            visionOnlyTags = listOf("EARLY_BODY_OPEN"),
            visionOnlyFeedback = "상체 회전이 빠릅니다."
        )

        assertEquals(0.5f, score.tagJaccardDistance, 0.001f)
        assertTrue(score.isJaccardCriteriaMet)
        assertTrue(score.hasCausalExplanation)
        assertEquals(2, score.kineticChainStageGain)
        assertTrue(score.overallPass)
    }

    @Test
    fun `AC-3 identical tagsets produce Jaccard distance 0 and fails criteria`() {
        val fused = createDummyFusedSwing(
            tags = listOf("EARLY_BODY_OPEN"),
            causalExplanation = "상체 회전 지연"
        )

        val score = evaluator.evaluate(
            fusedSwing = fused,
            visionOnlyTags = listOf("EARLY_BODY_OPEN"),
            visionOnlyFeedback = "상체 회전 지연"
        )

        assertEquals(0.0f, score.tagJaccardDistance, 0.001f)
        assertFalse(score.isJaccardCriteriaMet)
        assertFalse(score.overallPass)
    }

    @Test
    fun `empty fusion and vision tags yield Jaccard distance 0 without crashing`() {
        val fused = createDummyFusedSwing(
            tags = emptyList(),
            causalExplanation = "",
        )

        val score = evaluator.evaluate(
            fusedSwing = fused,
            visionOnlyTags = emptyList(),
            visionOnlyFeedback = "",
        )

        assertEquals(0.0f, score.tagJaccardDistance, 0.001f)
        assertFalse(score.isJaccardCriteriaMet)
        assertFalse(score.hasCausalExplanation)
        assertFalse(score.overallPass)
    }

    @Test
    fun `AC-4 causal keywords detection validates causal explanation`() {
        val fused = createDummyFusedSwing(
            tags = listOf("FACE_OPEN", "EARLY_BODY_OPEN"),
            causalExplanation = "골반 회전 부족으로 인해 상체가 조기 회전되어 타점이 뒤로 형성된 것이 원인입니다."
        )

        val score = evaluator.evaluate(
            fusedSwing = fused,
            visionOnlyTags = listOf("EARLY_BODY_OPEN"),
            visionOnlyFeedback = "골반을 더 돌리세요."
        )

        assertTrue(score.hasCausalExplanation)
    }

    @Test
    fun `AC-5 5-stage kinetic chain achieves kineticChainStageGain 2`() {
        val fused = createDummyFusedSwing(
            tags = listOf("CLEAN_STRIKE", "OPTIMAL_CHAIN", "SQUARE_FACE"),
            causalExplanation = "골반부터 라켓까지 순차적으로 가속되어 최대의 에너지가 스퀘어 페이스로 전달되었습니다."
        )

        val score = evaluator.evaluate(
            fusedSwing = fused,
            visionOnlyTags = listOf("GOOD_SWING"),
            visionOnlyFeedback = "좋은 스윙입니다."
        )

        assertEquals(2, score.kineticChainStageGain)
        assertTrue(score.overallPass)
    }

    private fun createPoseSequence(hip: Int = 3, shoulder: Int = 4, wrist: Int = 5): List<PoseFrame> {
        return (0..60).map { frameIdx ->
            val landmarks = (0..32).map { jointIdx ->
                val x = when (jointIdx) {
                    24 -> if (frameIdx == hip) 0.8f else 0.2f
                    12 -> if (frameIdx == shoulder) 0.8f else 0.2f
                    16 -> if (frameIdx == wrist) 0.9f else 0.2f
                    else -> 0.5f
                }
                PoseLandmark(x, 0.5f, 0.0f, 1.0f)
            }
            PoseFrame(landmarks)
        }
    }

    private fun createImuSequence(racketTs: Long = 210L, impactTs: Long = 230L, gyroY: Float = 0f): List<ImuDataPoint> {
        val samples = mutableListOf<ImuDataPoint>()
        var ts = 0L
        while (ts <= 1000L) {
            val gyro = if (ts == racketTs) 1800f else 100f
            val accel = if (ts == impactTs) 25f else 1f
            val gY = if (ts == impactTs) gyroY else 0f
            samples.add(
                ImuDataPoint(
                    timestampMs = ts,
                    accelX = accel,
                    accelY = 0f,
                    accelZ = 0f,
                    gyroX = 0f,
                    gyroY = gY,
                    gyroZ = gyro
                )
            )
            ts += 10L
        }
        return samples
    }

    @Test
    fun `AC-6 evaluateDataset with 5 diverse test cases produces 100 percent pass rate and avg DJ over threshold`() {
        val testCases = listOf(
            // Case 1: Early Body Open + Face Open
            AblationTestCase(
                testCaseId = "case-1-face-open",
                drillType = DrillType.FOREHAND,
                poses = createPoseSequence(hip = 5, shoulder = 3, wrist = 6),
                imuSamples = createImuSequence(racketTs = 250L, impactTs = 280L, gyroY = 600f),
                visionOnlyTags = listOf("EARLY_BODY_OPEN"),
                visionOnlyFeedback = "상체가 먼저 열립니다."
            ),
            // Case 2: Late Contact + Face Closed
            AblationTestCase(
                testCaseId = "case-2-face-closed",
                drillType = DrillType.FOREHAND,
                poses = createPoseSequence(hip = 3, shoulder = 4, wrist = 5),
                imuSamples = createImuSequence(racketTs = 210L, impactTs = 230L, gyroY = -600f),
                visionOnlyTags = listOf("LATE_CONTACT"),
                visionOnlyFeedback = "타점이 늦습니다."
            ),
            // Case 3: Power Leak (timing delay)
            AblationTestCase(
                testCaseId = "case-3-power-leak",
                drillType = DrillType.FOREHAND,
                poses = createPoseSequence(hip = 2, shoulder = 3, wrist = 4),
                imuSamples = createImuSequence(racketTs = 310L, impactTs = 230L, gyroY = 0f),
                visionOnlyTags = listOf("SLOW_SWING"),
                visionOnlyFeedback = "스윙 속도가 느립니다."
            ),
            // Case 4: Clean Strike (perfect sequence + square face)
            AblationTestCase(
                testCaseId = "case-4-clean-strike",
                drillType = DrillType.FOREHAND,
                poses = createPoseSequence(hip = 3, shoulder = 4, wrist = 5),
                imuSamples = createImuSequence(racketTs = 210L, impactTs = 230L, gyroY = 50f),
                visionOnlyTags = listOf("GOOD_SWING"),
                visionOnlyFeedback = "좋은 스윙입니다."
            ),
            // Case 5: Unsynchronized Fallback
            AblationTestCase(
                testCaseId = "case-5-unsync",
                drillType = DrillType.FOREHAND,
                poses = createPoseSequence(hip = 3, shoulder = 4, wrist = 5),
                imuSamples = createImuSequence(racketTs = 800L, impactTs = 850L, gyroY = 0f), // sensor 850ms vs vision 166ms (diff > 150ms)
                visionOnlyTags = listOf("UNKNOWN"),
                visionOnlyFeedback = "분석 불가"
            )
        )

        val report = evaluator.evaluateDataset(testCases, fusionEngine)

        assertEquals(5, report.totalCases)
        assertEquals(5, report.passedCases)
        assertEquals(1.0f, report.passRate, 0.001f)
        assertTrue("Average Jaccard distance should be >= 0.3f, got ${report.averageJaccardDistance}", report.averageJaccardDistance >= 0.3f)
        assertTrue(report.summary.contains("5 passed (100.0%)"))
    }
}
