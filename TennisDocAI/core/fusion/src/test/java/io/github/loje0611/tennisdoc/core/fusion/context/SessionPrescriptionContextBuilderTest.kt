package io.github.loje0611.tennisdoc.core.fusion.context

import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineDistribution
import io.github.loje0611.tennisdoc.core.fusion.anomaly.PersonalBaseline
import io.github.loje0611.tennisdoc.core.fusion.anomaly.StatisticalAnomalyDetector
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPrescriptionContextBuilderTest {

    private val builder = SessionPrescriptionContextBuilder()

    private fun createMockSwing(
        id: String,
        isSequential: Boolean = true,
        efficiency: Float = 85.0f,
        faceState: RacketFaceState = RacketFaceState.SQUARE,
        deviationDeg: Float = 2.0f,
        diagnosisTags: List<String> = emptyList()
    ): FusedSwing {
        return FusedSwing(
            swingId = id,
            sessionId = "test-session",
            drillType = DrillType.FOREHAND,
            anchor = SyncAnchor(100L, 100L, 0L, 0.9f, true),
            kineticChain = KineticChain5Stage(
                stages = listOf(
                    KineticStage(KineticStageType.HIP, 100L, 20f, 0L),
                    KineticStage(KineticStageType.SHOULDER, 120L, 30f, 20L),
                    KineticStage(KineticStageType.WRIST, 140L, 40f, 20L),
                    KineticStage(KineticStageType.RACKET, 160L, 80f, 20L),
                    KineticStage(KineticStageType.IMPACT, 170L, 100f, 10L)
                ),
                isSequential = isSequential,
                totalDurationMs = 70L,
                energyTransferEfficiency = efficiency
            ),
            racketImpact = RacketImpactOrientation(
                rollDeg = 0f, pitchDeg = 0f, yawDeg = 0f,
                faceState = faceState,
                deviationDeg = deviationDeg
            ),
            visionPoses = listOf(
                PoseFrame(
                    landmarks = listOf(
                        PoseLandmark(x = 0.424242f, y = 0.535353f, z = 0.646464f, visibility = 0.919191f),
                    ),
                ),
            ),
            imuSamples = listOf(
                ImuDataPoint(100L, 1.111111f, 2f, 3f, 0.1f, 0.2f, 9.876543f),
            ),
            diagnosis = FusionDiagnosis(
                diagnosisTags = diagnosisTags,
                primaryCause = "test cause",
                coachingFeedback = "test feedback",
                causalExplanation = "test explanation"
            )
        )
    }

    @Test
    fun testEmptySessionReturnsDefault() {
        val ctx = builder.buildContext("s1", DrillType.FOREHAND, emptyList())
        assertEquals(0, ctx.totalSwingCount)
        assertEquals(0, ctx.sequentialChainRatePercent)
        assertEquals(0f, ctx.averageEnergyEfficiency)
        assertNull(ctx.primaryFlawTag)
        assertTrue(ctx.representativeFlaws.isEmpty())
        assertFalse(ctx.isFatigued)
        assertTrue(ctx.toJsonString().startsWith("{\"sessionId\":\"s1\""))
    }

    @Test
    fun testAccurateAggregationAndPrimaryFlaw() {
        val swings = listOf(
            createMockSwing("s1", true, 90f, RacketFaceState.SQUARE, 1f, listOf("CLEAN_STRIKE")),
            createMockSwing("s2", false, 60f, RacketFaceState.OPEN, 15f, listOf("FACE_OPEN", "LATE_HIT")),
            createMockSwing("s3", true, 80f, RacketFaceState.OPEN, 10f, listOf("FACE_OPEN")),
            createMockSwing("s4", false, 50f, RacketFaceState.CLOSED, 12f, listOf("EARLY_BODY_OPEN"))
        )

        val ctx = builder.buildContext("s1", DrillType.FOREHAND, swings, null, 120L)

        assertEquals(4, ctx.totalSwingCount)
        assertEquals(50, ctx.sequentialChainRatePercent) // 2/4
        assertEquals(70f, ctx.averageEnergyEfficiency) // (90+60+80+50)/4
        assertEquals(90f, ctx.maxEnergyEfficiency)
        
        assertEquals(25, ctx.squareFaceRatePercent)
        assertEquals(50, ctx.openFaceRatePercent)
        assertEquals(25, ctx.closedFaceRatePercent)
        
        assertEquals(2, ctx.flawTagCounts["FACE_OPEN"])
        assertEquals("FACE_OPEN", ctx.primaryFlawTag)
        
        // representative flaws should pick up to 2 swings with flaws, sorted by lowest efficiency
        assertEquals(2, ctx.representativeFlaws.size)
        assertEquals("s4", ctx.representativeFlaws[0].swingId) // eff = 50
        assertEquals("s2", ctx.representativeFlaws[1].swingId) // eff = 60
        
        assertFalse(ctx.isFatigued)
        assertEquals(120L, ctx.durationSeconds)
        assertEquals(70L, ctx.averageChainDurationMs)
        assertEquals(StageDelaysSummary(20L, 20L, 20L, 10L), ctx.stageDelaysMs)
        assertEquals(9.5f, ctx.averageFaceDeviationDeg)
        assertEquals(80f, ctx.averageRacketSpeed)
        assertEquals(1, ctx.flawTagCounts["LATE_HIT"])
        assertEquals(1, ctx.flawTagCounts["EARLY_BODY_OPEN"])
        assertFalse(ctx.flawTagCounts.containsKey("CLEAN_STRIKE"))
        assertEquals(setOf("s4", "s2"), ctx.representativeFlaws.map { it.swingId }.toSet())
        assertFalse(ctx.representativeFlaws.any { it.swingId == "s1" })
        assertEquals(emptyList<String>(), ctx.representativeFlaws.flatMap { it.diagnosisTags }.filter { it == "CLEAN_STRIKE" })
        assertEquals(0, ctx.baselineAnomalyCount)
    }

    @Test
    fun testPrivacyPreservingJsonSerialization() {
        val swings = listOf(createMockSwing("s1", true, 90f, RacketFaceState.SQUARE, 1f, listOf("CLEAN_STRIKE")))
        val ctx = builder.buildContext("s1", DrillType.FOREHAND, swings)
        val jsonStr = ctx.toJsonString()

        // MUST NOT contain privacy leaking keys
        assertFalse(jsonStr.contains("visionPoses"))
        assertFalse(jsonStr.contains("landmarks"))
        assertFalse(jsonStr.contains("imuSamples"))
        assertFalse(jsonStr.contains("accelX"))

        // MUST contain aggregated metrics
        assertTrue(jsonStr.contains("averageEnergyEfficiency"))
        assertTrue(jsonStr.contains("squareFaceRatePercent"))
    }

    @Test
    fun testBaselineAnomalies() {
        val baseline = PersonalBaseline(
            drillType = DrillType.FOREHAND,
            totalSwings = 10,
            distributions = mapOf(
                StatisticalAnomalyDetector.KEY_RACKET_SPEED to BaselineDistribution(10, 80f, 4f, 2f),
                StatisticalAnomalyDetector.KEY_ENERGY_EFFICIENCY to BaselineDistribution(10, 85f, 4f, 2f)
            ),
            isReliable = true
        )

        // swing s1 has racket speed 80, efficiency 60 (60 is far from 85 -> anomaly)
        val swings = listOf(
            createMockSwing("s1", false, 60f, RacketFaceState.OPEN, 10f, listOf("FACE_OPEN"))
        )

        val ctx = builder.buildContext("s1", DrillType.FOREHAND, swings, baseline)
        
        // Fatigue should be detected because efficiency is very low
        // Actually, FatigueAnalysis depends on average Z scores across session
        assertTrue(ctx.baselineAnomalyCount > 0)
    }

    @Test
    fun ac1_contextIsDataClassCopyIndependent() {
        val ctx = builder.buildContext(
            "sess-copy",
            DrillType.SERVE,
            listOf(createMockSwing("s1", true, 90f, RacketFaceState.SQUARE, 1f, listOf("CLEAN_STRIKE"))),
            durationSeconds = 30L,
        )
        val copy = ctx.copy(sessionId = "other")
        assertEquals("sess-copy", ctx.sessionId)
        assertEquals("other", copy.sessionId)
        assertEquals(DrillType.SERVE, ctx.drillType)
        assertEquals(ctx.totalSwingCount, copy.totalSwingCount)
    }

    @Test
    fun ac1_flawTagCountsAreNotExternallyMutable() {
        val ctx = builder.buildContext(
            "s1",
            DrillType.FOREHAND,
            listOf(
                createMockSwing("s2", false, 60f, RacketFaceState.OPEN, 15f, listOf("FACE_OPEN")),
            ),
        )
        val original = ctx.flawTagCounts.toMap()
        val mutable = ctx.flawTagCounts as? MutableMap<String, Int>
        mutable?.put("HACKED_TAG", 99)
        assertEquals(original, ctx.flawTagCounts)
        assertFalse(ctx.flawTagCounts.containsKey("HACKED_TAG"))
    }

    @Test
    fun ac2_singleSwingAveragesMatchMaxima() {
        val ctx = builder.buildContext(
            "solo",
            DrillType.BACKHAND,
            listOf(createMockSwing("only", true, 77f, RacketFaceState.CLOSED, 8f, listOf("FACE_CLOSED"))),
            durationSeconds = 45L,
        )
        assertEquals(1, ctx.totalSwingCount)
        assertEquals(100, ctx.sequentialChainRatePercent)
        assertEquals(77f, ctx.averageEnergyEfficiency)
        assertEquals(77f, ctx.maxEnergyEfficiency)
        assertEquals(0, ctx.squareFaceRatePercent)
        assertEquals(0, ctx.openFaceRatePercent)
        assertEquals(100, ctx.closedFaceRatePercent)
        assertEquals("FACE_CLOSED", ctx.primaryFlawTag)
        assertEquals(1, ctx.representativeFlaws.size)
        assertEquals("only", ctx.representativeFlaws.single().swingId)
    }

    @Test
    fun ac2_allBrokenChainsYieldZeroSequentialRate() {
        val swings = listOf(
            createMockSwing("a", false, 40f, RacketFaceState.OPEN, 12f, listOf("EARLY_BODY_OPEN")),
            createMockSwing("b", false, 41f, RacketFaceState.OPEN, 11f, listOf("EARLY_BODY_OPEN", "FACE_OPEN")),
            createMockSwing("c", false, 42f, RacketFaceState.CLOSED, 9f, listOf("EARLY_BODY_OPEN")),
        )
        val ctx = builder.buildContext("broken", DrillType.FOREHAND, swings)
        assertEquals(0, ctx.sequentialChainRatePercent)
        assertEquals("EARLY_BODY_OPEN", ctx.primaryFlawTag)
        assertEquals(3, ctx.flawTagCounts["EARLY_BODY_OPEN"])
    }

    @Test
    fun ac4_jsonOmitsLandmarkCoordinatesImuSeriesAndStaysUnder2kb() {
        val swings = List(4) { index ->
            createMockSwing(
                id = "priv-$index",
                isSequential = index % 2 == 0,
                efficiency = 50f + index,
                faceState = if (index == 0) RacketFaceState.SQUARE else RacketFaceState.OPEN,
                deviationDeg = index.toFloat(),
                diagnosisTags = if (index == 0) listOf("CLEAN_STRIKE") else listOf("FACE_OPEN"),
            )
        }
        val json = builder.buildContext("priv-session", DrillType.FOREHAND, swings, durationSeconds = 90L)
            .toJsonString()

        assertFalse(json.contains("landmarks"))
        assertFalse(json.contains("visionPoses"))
        assertFalse(json.contains("imuSamples"))
        assertFalse(json.contains("accelX"))
        assertFalse(json.contains("gyroZ"))
        assertFalse(json.contains("visibility"))
        assertFalse(Regex(""""x"\s*:""").containsMatchIn(json))
        assertFalse(Regex(""""y"\s*:""").containsMatchIn(json))
        assertFalse(Regex(""""z"\s*:""").containsMatchIn(json))
        assertFalse(json.contains("0.424242"))
        assertFalse(json.contains("0.535353"))
        assertFalse(json.contains("9.876543"))
        assertTrue(json.length <= 2048)
        assertTrue(json.contains("\"sessionId\":\"priv-session\""))
    }

    @Test
    fun ac5_emptySessionJsonHasZeroRatesAndNoRawSeries() {
        val ctx = builder.buildContext("empty", DrillType.SERVE, emptyList(), durationSeconds = 12L)
        assertEquals(0, ctx.squareFaceRatePercent)
        assertEquals(0, ctx.openFaceRatePercent)
        assertEquals(0, ctx.closedFaceRatePercent)
        assertEquals(0f, ctx.maxEnergyEfficiency)
        assertEquals(0L, ctx.averageChainDurationMs)
        assertEquals(StageDelaysSummary(0, 0, 0, 0), ctx.stageDelaysMs)
        assertEquals(12L, ctx.durationSeconds)
        val json = ctx.toJsonString()
        assertFalse(json.contains("landmarks"))
        assertFalse(json.contains("accelX"))
        assertTrue(json.contains("\"primaryFlawTag\":null"))
    }
}
