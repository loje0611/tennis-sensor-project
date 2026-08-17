package io.github.loje0611.tennisdoc.core.coach.service

import io.github.loje0611.tennisdoc.core.coach.client.GeminiCoachClient
import io.github.loje0611.tennisdoc.core.coach.client.LlmCoachClient
import io.github.loje0611.tennisdoc.core.coach.client.MockLlmCoachClient
import io.github.loje0611.tennisdoc.core.coach.engine.LocalRuleBasedCoachEngine
import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.LlmProvider

class CompositeAiCoachService(
    private val geminiClientFactory: (apiKey: String) -> LlmCoachClient = { GeminiCoachClient(it) },
    private val mockClient: LlmCoachClient = MockLlmCoachClient(),
    private val fallbackEngine: LocalRuleBasedCoachEngine = LocalRuleBasedCoachEngine()
) {
    suspend fun createReport(
        context: SessionPrescriptionContext,
        provider: LlmProvider = LlmProvider.GEMINI,
        apiKey: String? = null,
        tone: CoachTone = CoachTone.ENCOURAGING
    ): AiCoachReport {
        if (provider == LlmProvider.MOCK) {
            val result = mockClient.generateReport(context, tone)
            if (result.isSuccess) return result.getOrThrow()
            return fallbackEngine.generateFallbackReport(context, tone)
        }
        
        if (provider == LlmProvider.GEMINI) {
            if (apiKey.isNullOrBlank()) {
                return fallbackEngine.generateFallbackReport(context, tone)
            }
            
            val client = geminiClientFactory(apiKey)
            val result = client.generateReport(context, tone)
            if (result.isSuccess) {
                return result.getOrThrow()
            }
            
            // On any failure (network, parsing, etc.), fallback to local engine
            return fallbackEngine.generateFallbackReport(context, tone)
        }
        
        // For OPENAI or unsupported providers, fallback directly
        return fallbackEngine.generateFallbackReport(context, tone)
    }
}
