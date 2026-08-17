package io.github.loje0611.tennisdoc.core.coach

import io.github.loje0611.tennisdoc.core.coach.client.GeminiCoachClient
import io.github.loje0611.tennisdoc.core.coach.engine.LocalRuleBasedCoachEngine
import io.github.loje0611.tennisdoc.core.coach.network.HttpResponse
import io.github.loje0611.tennisdoc.core.coach.network.HttpTransport
import io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService
import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.fusion.context.StageDelaysSummary
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.LlmProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackEngineTest {

    private fun createDummyContext(flaw: String? = "EARLY_BODY_OPEN"): SessionPrescriptionContext {
        return SessionPrescriptionContext(
            sessionId = "test-session",
            drillType = DrillType.FOREHAND,
            totalSwingCount = 10,
            durationSeconds = 600,
            sequentialChainRatePercent = 80,
            averageEnergyEfficiency = 85f,
            maxEnergyEfficiency = 95f,
            averageChainDurationMs = 70,
            stageDelaysMs = StageDelaysSummary(20, 20, 20, 10),
            squareFaceRatePercent = 75,
            openFaceRatePercent = 25,
            closedFaceRatePercent = 0,
            averageFaceDeviationDeg = 5f,
            averageRacketSpeed = 90f,
            flawTagCounts = flaw?.let { mapOf(it to 5) } ?: emptyMap(),
            primaryFlawTag = flaw,
            representativeFlaws = emptyList(),
            isFatigued = false,
            fatigueScore = 0f,
            baselineAnomalyCount = 0
        )
    }

    class FakeHttpTransport(
        private val statusCode: Int,
        private val responseBody: String,
    ) : HttpTransport {
        var postCount = 0
            private set
        var lastUrl: String? = null
            private set
        var lastBody: String? = null
            private set

        override suspend fun postJson(
            url: String,
            headers: Map<String, String>,
            bodyJson: String,
            timeoutMs: Long,
        ): HttpResponse {
            postCount++
            lastUrl = url
            lastBody = bodyJson
            if (statusCode == -1) throw Exception("Network disconnected")
            return HttpResponse(statusCode, responseBody)
        }
    }

    @Test
    fun testLocalRuleBasedCoachEngineFallbackReport() {
        val engine = LocalRuleBasedCoachEngine()
        val context = createDummyContext()
        val report = engine.generateFallbackReport(context, CoachTone.ENCOURAGING)

        assertTrue(report.isFallbackReport)
        assertEquals("local-rule-engine", report.rawModelName)
        assertEquals("EARLY_BODY_OPEN", report.primaryFlawDiagnosis?.flawTitle)
        assertTrue(report.keyStrengths.contains("우수한 임팩트 정타율"))
        assertTrue(report.keyStrengths.contains("높은 에너지 전송 효율"))
        assertEquals("test-session", report.sessionId)
        assertTrue(report.overallSummary.contains("훌륭한 노력"))
        assertTrue(report.overallSummary.contains("운동 체인이 부드럽게"))
        assertTrue(report.primaryFlawDiagnosis!!.observedEffect.contains("상체"))
        assertTrue(report.primaryFlawDiagnosis!!.coachingCue.contains("하체"))
        assertEquals(DrillType.FOREHAND, report.recommendedDrills.single().drillType)
        assertTrue(report.recommendedDrills.single().targetRepetitions in 10..20)
    }

    @Test
    fun ac1_faceOpenAndCleanStrikeMapKoreanDiagnosis() {
        val engine = LocalRuleBasedCoachEngine()
        val open = engine.generateFallbackReport(createDummyContext("FACE_OPEN"), CoachTone.STRICT)
        assertTrue(open.isFallbackReport)
        assertTrue(open.overallSummary.contains("결과에 집중"))
        assertEquals("FACE_OPEN", open.primaryFlawDiagnosis?.flawTitle)
        assertTrue(open.primaryFlawDiagnosis!!.coachingCue.contains("스퀘어"))

        val clean = engine.generateFallbackReport(createDummyContext("CLEAN_STRIKE"))
        assertEquals("CLEAN_STRIKE", clean.primaryFlawDiagnosis?.flawTitle)
        assertTrue(clean.primaryFlawDiagnosis!!.coachingCue.contains("밸런스") || clean.primaryFlawDiagnosis!!.observedEffect.contains("정타"))

        val late = engine.generateFallbackReport(createDummyContext("LATE_CONTACT"))
        assertEquals("LATE_CONTACT", late.primaryFlawDiagnosis?.flawTitle)
        assertTrue(late.primaryFlawDiagnosis!!.coachingCue.contains("앞쪽"))
    }

    @Test
    fun testGeminiCoachClientSuccess() = runBlocking {
        val validJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\n{\"overallSummary\":\"Remote OK\",\"keyStrengths\":[],\"actionItems\":[],\"recommendedDrills\":[]}\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        
        val transport = FakeHttpTransport(200, validJson)
        val client = GeminiCoachClient("valid_api_key", transport = transport)
        
        val result = client.generateReport(createDummyContext(), CoachTone.ENCOURAGING)
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertFalse(report.isFallbackReport)
        assertEquals("Remote OK", report.overallSummary)
        assertEquals("test-session", report.sessionId)
        assertEquals("gemini-1.5-flash", report.rawModelName)
        assertTrue(transport.lastUrl!!.contains("generativelanguage.googleapis.com"))
        assertTrue(transport.lastUrl!!.contains("gemini-1.5-flash"))
        assertEquals(1, transport.postCount)
        assertTrue(transport.lastBody!!.contains("SESSION_DATA") || transport.lastBody!!.contains("sess") || transport.lastBody!!.contains("test-session"))
    }

    @Test
    fun testGeminiCoachClientNetworkError() = runBlocking {
        val transport = FakeHttpTransport(500, "Internal Server Error")
        val client = GeminiCoachClient("valid_api_key", transport = transport)
        
        val result = client.generateReport(createDummyContext(), CoachTone.ENCOURAGING)
        assertTrue(result.isFailure)
        
        val transport2 = FakeHttpTransport(-1, "")
        val client2 = GeminiCoachClient("valid_api_key", transport = transport2)
        val result2 = client2.generateReport(createDummyContext(), CoachTone.ENCOURAGING)
        assertTrue(result2.isFailure)

        val unauthorized = FakeHttpTransport(401, "Unauthorized")
        val client401 = GeminiCoachClient("bad_key", transport = unauthorized)
        val result401 = client401.generateReport(createDummyContext(), CoachTone.ENCOURAGING)
        assertTrue(result401.isFailure)
    }

    @Test
    fun testCompositeServiceMissingApiKeyFallback() = runBlocking {
        val service = CompositeAiCoachService()
        val report = service.createReport(
            context = createDummyContext(),
            provider = LlmProvider.GEMINI,
            apiKey = null
        )
        
        assertTrue(report.isFallbackReport)
        assertEquals("local-rule-engine", report.rawModelName)
    }

    @Test
    fun testCompositeServiceNetworkFailureAutoFallback() = runBlocking {
        val transport = FakeHttpTransport(500, "Error")
        val clientFactory = { apiKey: String -> GeminiCoachClient(apiKey, transport = transport) }
        val service = CompositeAiCoachService(geminiClientFactory = clientFactory)
        
        val report = service.createReport(
            context = createDummyContext(),
            provider = LlmProvider.GEMINI,
            apiKey = "valid_api_key"
        )
        
        // Even though Gemini was requested with a valid API key, the 500 error causes a seamless fallback
        assertTrue(report.isFallbackReport)
        assertEquals("local-rule-engine", report.rawModelName)
    }

    @Test
    fun ac4_blankApiKeyDoesNotCallTransport() = runBlocking {
        val transport = FakeHttpTransport(200, "{}")
        val service = CompositeAiCoachService(
            geminiClientFactory = { GeminiCoachClient(it, transport = transport) },
        )
        val report = service.createReport(
            context = createDummyContext(),
            provider = LlmProvider.GEMINI,
            apiKey = "   ",
        )
        assertTrue(report.isFallbackReport)
        assertEquals("local-rule-engine", report.rawModelName)
        assertEquals(0, transport.postCount)
    }

    @Test
    fun ac5_invalidGeminiJsonFallsBackWithoutCrash() = runBlocking {
        val transport = FakeHttpTransport(
            200,
            """{"candidates":[{"content":{"parts":[{"text":"not json at all"}]}}]}""",
        )
        val service = CompositeAiCoachService(
            geminiClientFactory = { GeminiCoachClient(it, transport = transport) },
        )
        val report = service.createReport(
            context = createDummyContext("FACE_OPEN"),
            provider = LlmProvider.GEMINI,
            apiKey = "valid_api_key",
        )
        assertTrue(report.isFallbackReport)
        assertEquals("local-rule-engine", report.rawModelName)
        assertEquals("FACE_OPEN", report.primaryFlawDiagnosis?.flawTitle)
        assertEquals(1, transport.postCount)
    }

    @Test
    fun ac5_mockProviderReturnsNonFallbackReport() = runBlocking {
        val report = CompositeAiCoachService().createReport(
            context = createDummyContext(),
            provider = LlmProvider.MOCK,
            apiKey = null,
        )
        assertFalse(report.isFallbackReport)
        assertEquals("mock-model", report.rawModelName)
        assertEquals("test-session", report.sessionId)
    }
}
