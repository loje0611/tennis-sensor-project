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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class StatisticalAnomalyDetectorGoldenFixtureTest {

    private val detector = StatisticalAnomalyDetector()

    @Test
    fun `golden JSON fixtures all pass`() {
        val fixture = loadFixture()
        val speeds = fixture.getJSONArray("baseline_speeds")
        var baseline: PersonalBaseline? = null
        for (i in 0 until speeds.length()) {
            baseline = detector.updateBaseline(
                baseline,
                createSwing(racketSpeed = speeds.getDouble(i).toFloat()),
            )
        }
        assertNotNull(baseline)
        assertEquals(speeds.length(), baseline!!.totalSwings)
        assertTrue(baseline.isReliable)

        val speedDist = baseline.distributions[StatisticalAnomalyDetector.KEY_RACKET_SPEED]!!
        val expectedMean = fixture.getDouble("expected_mean").toFloat()
        val expectedStd = fixture.getDouble("expected_std_dev").toFloat()
        val stdTol = fixture.getDouble("std_dev_tolerance").toFloat()
        assertEquals("baseline mean", expectedMean, speedDist.mean, 0.01f)
        assertEquals("baseline stdDev", expectedStd, speedDist.stdDev, stdTol)

        val cases = fixture.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val name = case.getString("name")
            if (case.has("recent_swings")) {
                val recentArr = case.getJSONArray("recent_swings")
                val recent = (0 until recentArr.length()).map { idx ->
                    val s = recentArr.getJSONObject(idx)
                    createSwing(
                        racketSpeed = s.getDouble("racket_speed").toFloat(),
                        efficiency = s.getDouble("efficiency").toFloat(),
                        wristRacketDelay = s.getLong("wrist_racket_delay_ms"),
                    )
                }
                val fatigue = detector.analyzeFatigueTrend(recent, baseline)
                assertEquals("$name isFatigued", case.getBoolean("expected_is_fatigued"), fatigue.isFatigued)
                assertTrue(
                    "$name fatigueScore ${fatigue.fatigueScore}",
                    fatigue.fatigueScore >= case.getDouble("min_fatigue_score").toFloat(),
                )
                assertNotNull("$name summary", fatigue.formBreakdownSummary)
                assertTrue(
                    "$name summary",
                    fatigue.formBreakdownSummary!!.contains(case.getString("summary_contains")),
                )
            } else {
                val swing = createSwing(
                    racketSpeed = case.getDouble("racket_speed").toFloat(),
                    efficiency = case.getDouble("efficiency").toFloat(),
                    wristRacketDelay = case.getLong("wrist_racket_delay_ms"),
                )
                val report = detector.detectAnomalies(baseline, swing)
                val anomaly = report.anomalies.first {
                    it.metricKey == StatisticalAnomalyDetector.KEY_RACKET_SPEED
                }
                assertEquals("$name isAnomaly", case.getBoolean("expected_is_anomaly"), anomaly.isAnomaly)
                assertEquals(
                    "$name severity",
                    AnomalySeverity.valueOf(case.getString("expected_severity")),
                    anomaly.severity,
                )
                if (case.has("max_abs_z")) {
                    assertTrue("$name |z|", abs(anomaly.zScore) < case.getDouble("max_abs_z").toFloat())
                }
                if (case.has("max_z")) {
                    assertTrue("$name z", anomaly.zScore <= case.getDouble("max_z").toFloat())
                }
            }
        }

        val unreliableN = fixture.getInt("unreliable_sample_count")
        var shortBaseline: PersonalBaseline? = null
        repeat(unreliableN) {
            shortBaseline = detector.updateBaseline(shortBaseline, createSwing())
        }
        assertFalse(shortBaseline!!.isReliable)
        assertEquals(unreliableN, shortBaseline!!.totalSwings)
    }

    private fun loadFixture(): JSONObject {
        val uri = javaClass.classLoader?.getResource("golden_anomaly_baseline_fixture.json")
            ?: throw IllegalStateException("Cannot find golden_anomaly_baseline_fixture.json")
        return JSONObject(File(uri.toURI()).readText())
    }

    private fun createSwing(
        racketSpeed: Float = 1500f,
        efficiency: Float = 90f,
        wristRacketDelay: Long = 30L,
        totalDuration: Long = 130L,
    ): FusedSwing {
        val stages = listOf(
            KineticStage(KineticStageType.HIP, 1000L, 10f),
            KineticStage(KineticStageType.SHOULDER, 1030L, 15f, 30L),
            KineticStage(KineticStageType.WRIST, 1060L, 20f, 30L),
            KineticStage(KineticStageType.RACKET, 1060L + wristRacketDelay, racketSpeed, wristRacketDelay),
            KineticStage(KineticStageType.IMPACT, 1000L + totalDuration, 25f, 20L),
        )
        return FusedSwing(
            swingId = "test-swing",
            sessionId = "session-1",
            drillType = DrillType.FOREHAND_TOPSPIN,
            anchor = SyncAnchor(1000L, 1000L, 0L, 0.95f, true),
            kineticChain = KineticChain5Stage(stages, true, totalDuration, efficiency),
            racketImpact = RacketImpactOrientation(2f, 0f, 0f, RacketFaceState.SQUARE, 2f),
            visionPoses = emptyList(),
            imuSamples = emptyList(),
            diagnosis = FusionDiagnosis(
                diagnosisTags = listOf("CLEAN_STRIKE"),
                primaryCause = "정상 스윙",
                coachingFeedback = "피드백",
                causalExplanation = "설명",
            ),
        )
    }
}
