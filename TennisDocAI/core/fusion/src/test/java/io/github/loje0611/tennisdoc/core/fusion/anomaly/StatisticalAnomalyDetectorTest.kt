package io.github.loje0611.tennisdoc.core.fusion.anomaly

import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.FusionDiagnosis
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticalAnomalyDetectorTest {

    private val detector = StatisticalAnomalyDetector()

    private fun createSwing(
        racketSpeed: Float = 1500f,
        efficiency: Float = 90f,
        faceDev: Float = 2f,
        wristRacketDelay: Long = 30L,
        totalDuration: Long = 130L
    ): FusedSwing {
        val anchor = SyncAnchor(1000L, 1000L, 0L, 0.95f, true)
        val stages = listOf(
            KineticStage(KineticStageType.HIP, 1000L, 10f),
            KineticStage(KineticStageType.SHOULDER, 1030L, 15f, 30L),
            KineticStage(KineticStageType.WRIST, 1060L, 20f, 30L),
            KineticStage(KineticStageType.RACKET, 1060L + wristRacketDelay, racketSpeed, wristRacketDelay),
            KineticStage(KineticStageType.IMPACT, 1000L + totalDuration, 25f, 20L)
        )
        val chain = KineticChain5Stage(stages, true, totalDuration, efficiency)
        val orientation = RacketImpactOrientation(faceDev, 0f, 0f, RacketFaceState.SQUARE, faceDev)
        val diagnosis = FusionDiagnosis(
            diagnosisTags = listOf("CLEAN_STRIKE"),
            primaryCause = "정상 스윙",
            coachingFeedback = "피드백",
            causalExplanation = "설명"
        )
        return FusedSwing(
            swingId = "test-swing",
            sessionId = "session-1",
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
    fun `AC-2 10 normal swings correctly compute mean and stddev via Welford algorithm`() {
        var baseline: PersonalBaseline? = null
        val speeds = listOf(1480f, 1500f, 1520f, 1490f, 1510f, 1500f, 1530f, 1470f, 1500f, 1500f)

        for (speed in speeds) {
            baseline = detector.updateBaseline(baseline, createSwing(racketSpeed = speed))
        }

        assertNotNull(baseline)
        assertEquals(10, baseline!!.totalSwings)
        assertTrue(baseline.isReliable)

        val speedDist = baseline.distributions[StatisticalAnomalyDetector.KEY_RACKET_SPEED]
        assertNotNull(speedDist)
        assertEquals(1500f, speedDist!!.mean, 1.0f)
        assertTrue("StdDev should be positive around 16~18, got ${speedDist.stdDev}", speedDist.stdDev in 14f..20f)
    }

    @Test
    fun `AC-3 critical anomaly is detected when value deviates by more than 2_5 sigma`() {
        var baseline: PersonalBaseline? = null
        // Create baseline around mean 1500, stddev ~ 20
        val speeds = listOf(1480f, 1500f, 1520f, 1490f, 1510f, 1500f, 1530f, 1470f, 1500f, 1500f)
        for (speed in speeds) {
            baseline = detector.updateBaseline(baseline, createSwing(racketSpeed = speed))
        }

        // Outlier swing with speed = 1350 (deviation = -150, z ≈ -150 / 17.6 ≈ -8.5 sigma)
        val outlierSwing = createSwing(racketSpeed = 1350f)
        val report = detector.detectAnomalies(baseline!!, outlierSwing)

        val speedAnomaly = report.anomalies.first { it.metricKey == StatisticalAnomalyDetector.KEY_RACKET_SPEED }
        assertTrue(speedAnomaly.isAnomaly)
        assertEquals(AnomalySeverity.CRITICAL, speedAnomaly.severity)
        assertTrue("zScore should be <= -2.5, got ${speedAnomaly.zScore}", speedAnomaly.zScore <= -2.5f)
        assertTrue(speedAnomaly.description.contains("라켓 헤드 스피드"))
    }

    @Test
    fun `AC-4 normal swing within 1_0 sigma is marked as NORMAL severity and not anomaly`() {
        var baseline: PersonalBaseline? = null
        val speeds = listOf(1480f, 1500f, 1520f, 1490f, 1510f, 1500f, 1530f, 1470f, 1500f, 1500f)
        for (speed in speeds) {
            baseline = detector.updateBaseline(baseline, createSwing(racketSpeed = speed))
        }

        val normalSwing = createSwing(racketSpeed = 1505f)
        val report = detector.detectAnomalies(baseline!!, normalSwing)

        val speedAnomaly = report.anomalies.first { it.metricKey == StatisticalAnomalyDetector.KEY_RACKET_SPEED }
        assertFalse(speedAnomaly.isAnomaly)
        assertEquals(AnomalySeverity.NORMAL, speedAnomaly.severity)
    }

    @Test
    fun `AC-5 continuous speed drop and delay increase triggers fatigue detection`() {
        var baseline: PersonalBaseline? = null
        val speeds = listOf(1500f, 1500f, 1500f, 1500f, 1500f, 1500f)
        for (speed in speeds) {
            baseline = detector.updateBaseline(
                baseline,
                createSwing(racketSpeed = speed, efficiency = 90f, wristRacketDelay = 30L)
            )
        }

        // Recent swings with severe fatigue: low speed, low efficiency, high delay
        val fatiguedSwings = (1..5).map {
            createSwing(racketSpeed = 1200f, efficiency = 50f, wristRacketDelay = 80L)
        }

        val fatigueAnalysis = detector.analyzeFatigueTrend(fatiguedSwings, baseline!!)
        assertTrue("Expected isFatigued true, got false", fatigueAnalysis.isFatigued)
        assertTrue(fatigueAnalysis.fatigueScore >= 0.7f)
        assertNotNull(fatigueAnalysis.formBreakdownSummary)
        assertTrue(fatigueAnalysis.formBreakdownSummary!!.contains("피로 누적"))
    }

    @Test
    fun `AC-6 sample count less than 5 maintains isReliable false and suppresses anomaly alerts`() {
        var baseline: PersonalBaseline? = null
        for (i in 1..3) {
            baseline = detector.updateBaseline(baseline, createSwing(racketSpeed = 1500f))
        }

        assertNotNull(baseline)
        assertEquals(3, baseline!!.totalSwings)
        assertFalse(baseline.isReliable)

        // Even with extreme value, un-reliable baseline reports NORMAL with accumulation notice
        val swing = createSwing(racketSpeed = 900f)
        val report = detector.detectAnomalies(baseline, swing)

        val speedAnomaly = report.anomalies.first { it.metricKey == StatisticalAnomalyDetector.KEY_RACKET_SPEED }
        assertFalse(speedAnomaly.isAnomaly)
        assertEquals(AnomalySeverity.NORMAL, speedAnomaly.severity)
        assertTrue(speedAnomaly.description.contains("Baseline 축적 중 (3/5)"))
    }
}
