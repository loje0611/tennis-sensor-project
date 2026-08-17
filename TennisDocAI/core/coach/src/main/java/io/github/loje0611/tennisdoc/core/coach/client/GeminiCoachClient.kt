package io.github.loje0611.tennisdoc.core.coach.client

import io.github.loje0611.tennisdoc.core.coach.network.DefaultHttpTransport
import io.github.loje0611.tennisdoc.core.coach.network.HttpTransport
import io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser
import io.github.loje0611.tennisdoc.core.coach.prompt.CoachPromptBuilder
import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CoachTone
import org.json.JSONArray
import org.json.JSONObject

class GeminiCoachClient(
    private val apiKey: String,
    private val modelName: String = "gemini-1.5-flash",
    private val transport: HttpTransport = DefaultHttpTransport()
) : LlmCoachClient {

    private val promptBuilder = CoachPromptBuilder()
    private val parser = StructuredReportParser()

    override suspend fun generateReport(
        context: SessionPrescriptionContext,
        tone: CoachTone
    ): Result<AiCoachReport> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("API Key is blank"))
        }
        
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val prompt = promptBuilder.buildPrompt(context, tone)
            
            val payload = JSONObject()
            val contents = JSONArray()
            val content = JSONObject()
            val parts = JSONArray()
            val part = JSONObject()
            part.put("text", prompt)
            parts.put(part)
            content.put("parts", parts)
            contents.put(content)
            payload.put("contents", contents)
            
            val response = transport.postJson(url, emptyMap(), payload.toString())
            
            if (response.statusCode !in 200..299) {
                return Result.failure(Exception("HTTP error ${response.statusCode}: ${response.body}"))
            }
            
            val responseObj = JSONObject(response.body)
            val candidates = responseObj.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return Result.failure(Exception("No candidates found in Gemini response"))
            }
            
            val firstCandidate = candidates.getJSONObject(0)
            val contentObj = firstCandidate.optJSONObject("content")
            val partsArr = contentObj?.optJSONArray("parts")
            val text = partsArr?.optJSONObject(0)?.optString("text", "") ?: ""
            
            parser.parseReport(text, context.sessionId, rawModelName = modelName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
