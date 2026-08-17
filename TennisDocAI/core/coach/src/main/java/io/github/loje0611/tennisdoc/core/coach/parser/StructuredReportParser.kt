package io.github.loje0611.tennisdoc.core.coach.parser

import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CausalFlawDiagnosis
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.DrillRecommendation
import io.github.loje0611.tennisdoc.core.model.DrillType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

class StructuredReportParser {

    fun parseReport(
        rawResponse: String,
        sessionId: String,
        rawModelName: String? = null
    ): Result<AiCoachReport> {
        return try {
            val jsonStr = extractJsonBlock(rawResponse)
            val jsonObject = JSONObject(jsonStr)

            if (!jsonObject.has("overallSummary")) {
                return Result.failure(IllegalArgumentException("Missing required field 'overallSummary'"))
            }
            
            val overallSummary = jsonObject.getString("overallSummary")
            
            val keyStrengths = parseStringList(jsonObject.optJSONArray("keyStrengths"))
            val actionItems = parseStringList(jsonObject.optJSONArray("actionItems"))
            
            val primaryFlawDiagnosis = jsonObject.optJSONObject("primaryFlawDiagnosis")?.let {
                CausalFlawDiagnosis(
                    flawTitle = it.optString("flawTitle", ""),
                    observedEffect = it.optString("observedEffect", ""),
                    rootCause = it.optString("rootCause", ""),
                    coachingCue = it.optString("coachingCue", "")
                )
            }
            
            val recommendedDrills = mutableListOf<DrillRecommendation>()
            jsonObject.optJSONArray("recommendedDrills")?.let { drillsArray ->
                for (i in 0 until drillsArray.length()) {
                    val drillObj = drillsArray.optJSONObject(i) ?: continue
                    val drillTypeStr = drillObj.optString("drillType", "FOREHAND")
                    val drillType = try {
                        DrillType.valueOf(drillTypeStr)
                    } catch (e: Exception) {
                        DrillType.FOREHAND
                    }
                    
                    recommendedDrills.add(
                        DrillRecommendation(
                            drillType = drillType,
                            title = drillObj.optString("title", ""),
                            focusPoint = drillObj.optString("focusPoint", ""),
                            targetRepetitions = drillObj.optInt("targetRepetitions", 10)
                        )
                    )
                }
            }

            val report = AiCoachReport(
                reportId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                generatedAtMillis = System.currentTimeMillis(),
                overallSummary = overallSummary,
                keyStrengths = keyStrengths,
                primaryFlawDiagnosis = primaryFlawDiagnosis,
                actionItems = actionItems,
                recommendedDrills = recommendedDrills,
                isFallbackReport = false,
                rawModelName = rawModelName
            )
            
            Result.success(report)
        } catch (e: JSONException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseStringList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.optString(i, ""))
        }
        return list
    }

    private fun extractJsonBlock(text: String): String {
        val trimmed = text.trim()
        val jsonPattern = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```")
        val matchResult = jsonPattern.find(trimmed)
        if (matchResult != null) {
            return matchResult.groupValues[1].trim()
        }
        
        val startIndex = trimmed.indexOf('{')
        val endIndex = trimmed.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
            return trimmed.substring(startIndex, endIndex + 1)
        }
        
        return trimmed
    }
}
