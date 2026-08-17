package io.github.loje0611.tennisdoc.core.coach

import io.github.loje0611.tennisdoc.core.coach.client.MockLlmCoachClient
import io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser
import io.github.loje0611.tennisdoc.core.coach.prompt.CoachPromptBuilder
import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.fusion.context.StageDelaysSummary
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.DrillType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachModuleTest {

    private fun createDummyContext(eff: Float = 85f, sqRate: Int = 80, flaw: String? = "FACE_OPEN"): SessionPrescriptionContext {
        return SessionPrescriptionContext(
            sessionId = "sess-123",
            drillType = DrillType.FOREHAND,
            totalSwingCount = 10,
            durationSeconds = 600,
            sequentialChainRatePercent = 90,
            averageEnergyEfficiency = eff,
            maxEnergyEfficiency = 95f,
            averageChainDurationMs = 70,
            stageDelaysMs = StageDelaysSummary(20, 20, 20, 10),
            squareFaceRatePercent = sqRate,
            openFaceRatePercent = 20,
            closedFaceRatePercent = 0,
            averageFaceDeviationDeg = 5f,
            averageRacketSpeed = 90f,
            flawTagCounts = flaw?.let { mapOf(it to 3) } ?: emptyMap(),
            primaryFlawTag = flaw,
            representativeFlaws = emptyList(),
            isFatigued = false,
            fatigueScore = 0f,
            baselineAnomalyCount = 0
        )
    }

    @Test
    fun testPromptBuilderTonesAndContext() {
        val builder = CoachPromptBuilder()
        val context = createDummyContext()
        
        val p1 = builder.buildPrompt(context, CoachTone.ENCOURAGING)
        assertTrue(p1.contains(context.toJsonString()))
        assertTrue(p1.contains("칭찬과 긍정적인 피드백"))
        
        val p2 = builder.buildPrompt(context, CoachTone.ANALYTICAL)
        assertTrue(p2.contains("분석적인 어조"))
        
        val p3 = builder.buildPrompt(context, CoachTone.STRICT)
        assertTrue(p3.contains("단호한 프로 코치 어조"))
        assertTrue(p1.contains("[SESSION_DATA]"))
        assertTrue(p1.contains("\"sessionId\":\"sess-123\""))
        assertTrue(p1.contains("overallSummary"))
        assertTrue(p1.contains("환각") || p1.contains("Hallucination"))
    }

    @Test
    fun ac2_emptySessionStillEmbedsZeroCountContext() {
        val empty = createDummyContext().copy(
            totalSwingCount = 0,
            durationSeconds = 0,
            sequentialChainRatePercent = 0,
            averageEnergyEfficiency = 0f,
            maxEnergyEfficiency = 0f,
            squareFaceRatePercent = 0,
            openFaceRatePercent = 0,
            closedFaceRatePercent = 0,
            flawTagCounts = emptyMap(),
            primaryFlawTag = null,
        )
        val prompt = CoachPromptBuilder().buildPrompt(empty)
        assertTrue(prompt.contains(empty.toJsonString()))
        assertTrue(prompt.contains("\"totalSwingCount\":0"))
        assertTrue(prompt.contains("칭찬과 긍정적인 피드백"))
    }

    @Test
    fun testStructuredReportParserSuccessWithMarkdown() {
        val parser = StructuredReportParser()
        val markdownJson = """
            Here is the requested report:
            ```json
            {
              "overallSummary": "Great session!",
              "keyStrengths": ["Good rotation"],
              "primaryFlawDiagnosis": {
                "flawTitle": "LATE_HIT",
                "observedEffect": "Ball goes wide",
                "rootCause": "Hip rotation is delayed",
                "coachingCue": "Turn hips earlier"
              },
              "actionItems": ["Focus on timing"],
              "recommendedDrills": [
                {
                  "drillType": "FOREHAND",
                  "title": "Timing Drill",
                  "focusPoint": "Contact point in front",
                  "targetRepetitions": 15
                }
              ]
            }
            ```
            Hope this helps!
        """.trimIndent()
        
        val result = parser.parseReport(markdownJson, "sess-123", "test-model")
        
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals("sess-123", report.sessionId)
        assertEquals("Great session!", report.overallSummary)
        assertEquals(1, report.keyStrengths.size)
        
        assertNotNull(report.primaryFlawDiagnosis)
        assertEquals("LATE_HIT", report.primaryFlawDiagnosis?.flawTitle)
        
        assertEquals(1, report.recommendedDrills.size)
        assertEquals("Timing Drill", report.recommendedDrills[0].title)
        assertEquals(DrillType.FOREHAND, report.recommendedDrills[0].drillType)
        assertEquals(15, report.recommendedDrills[0].targetRepetitions)
        assertEquals("test-model", report.rawModelName)
        assertFalse(report.isFallbackReport)
        assertTrue(report.reportId.isNotBlank())
        assertEquals("Turn hips earlier", report.primaryFlawDiagnosis?.coachingCue)
        assertEquals(listOf("Focus on timing"), report.actionItems)
    }

    @Test
    fun ac3_parserAcceptsBareFenceAndPlainJson() {
        val parser = StructuredReportParser()
        val fenced = """
            ```
            {"overallSummary":"plain fence","keyStrengths":["A"]}
            ```
        """.trimIndent()
        val fencedReport = parser.parseReport(fenced, "sess-fence").getOrThrow()
        assertEquals("plain fence", fencedReport.overallSummary)
        assertEquals(listOf("A"), fencedReport.keyStrengths)

        val prefix = parser.parseReport("Note:\n{\"overallSummary\":\"prefix ok\"}\nThanks", "sess-p").getOrThrow()
        assertEquals("prefix ok", prefix.overallSummary)
        assertTrue(prefix.actionItems.isEmpty())
        assertTrue(prefix.recommendedDrills.isEmpty())
    }

    @Test
    fun testStructuredReportParserFailureAndResilience() {
        val parser = StructuredReportParser()
        
        // Non-JSON string
        val r1 = parser.parseReport("Just a normal text", "s1")
        assertTrue(r1.isFailure)
        
        // Missing required field
        val r2 = parser.parseReport("{\"keyStrengths\":[]}", "s1")
        assertTrue(r2.isFailure)
        
        // Empty JSON but has required field
        val r3 = parser.parseReport("{\"overallSummary\":\"ok\"}", "s1")
        assertTrue(r3.isSuccess)
        val report = r3.getOrThrow()
        assertEquals("ok", report.overallSummary)
        assertTrue(report.keyStrengths.isEmpty())
        assertNull(report.primaryFlawDiagnosis)
        assertTrue(report.actionItems.isEmpty())
        assertTrue(report.recommendedDrills.isEmpty())
        assertFalse(report.isFallbackReport)

        val empty = parser.parseReport("", "s1")
        assertTrue(empty.isFailure)
        val blank = parser.parseReport("   \n  ", "s1")
        assertTrue(blank.isFailure)
    }

    @Test
    fun ac3_reportIdsAreUniquePerParse() {
        val parser = StructuredReportParser()
        val json = "{\"overallSummary\":\"dup\"}"
        val a = parser.parseReport(json, "s1").getOrThrow().reportId
        val b = parser.parseReport(json, "s1").getOrThrow().reportId
        assertTrue(a.isNotBlank())
        assertTrue(b.isNotBlank())
        assertTrue(a != b)
    }

    @Test
    fun testMockLlmCoachClient() = runBlocking {
        val client = MockLlmCoachClient()
        val context = createDummyContext(eff = 60f, sqRate = 50, flaw = "EARLY_BODY_OPEN")
        
        val result = client.generateReport(context, CoachTone.STRICT)
        assertTrue(result.isSuccess)
        
        val report = result.getOrThrow()
        assertTrue(report.overallSummary.contains("STRICT"))
        assertTrue(report.overallSummary.contains("에너지 손실")) // Because eff is 60 < 80
        
        assertNotNull(report.primaryFlawDiagnosis)
        assertEquals("EARLY_BODY_OPEN", report.primaryFlawDiagnosis?.flawTitle)
        assertTrue(report.primaryFlawDiagnosis?.observedEffect?.contains("50%") == true)
        
        assertEquals(1, report.recommendedDrills.size)
        assertEquals(DrillType.FOREHAND, report.recommendedDrills[0].drillType)
        assertEquals("sess-123", report.sessionId)
        assertEquals("mock-model", report.rawModelName)
        assertFalse(report.isFallbackReport)
    }

    @Test
    fun ac5_mockClientUsesHighEfficiencyAndNullFlaw() = runBlocking {
        val client = MockLlmCoachClient()
        val context = createDummyContext(eff = 90f, sqRate = 80, flaw = null)
        val report = client.generateReport(context, CoachTone.ENCOURAGING).getOrThrow()
        assertTrue(report.overallSummary.contains("ENCOURAGING"))
        assertTrue(report.overallSummary.contains("우수한 세션") || report.overallSummary.contains("에너지 전송 효율"))
        assertNull(report.primaryFlawDiagnosis)
        assertEquals(DrillType.FOREHAND, report.recommendedDrills.single().drillType)
        assertTrue(report.keyStrengths.any { it.contains("90") })
    }
}
