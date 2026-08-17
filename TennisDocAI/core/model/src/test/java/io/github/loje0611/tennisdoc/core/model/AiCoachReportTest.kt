package io.github.loje0611.tennisdoc.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCoachReportTest {

    @Test
    fun ac1_aiCoachReportHoldsAllContractFields() {
        val report = AiCoachReport(
            reportId = "r-123",
            sessionId = "s-456",
            generatedAtMillis = 1_600_000_000L,
            overallSummary = "Good effort, but watch your racket face.",
            keyStrengths = listOf("Fast swing speed"),
            primaryFlawDiagnosis = CausalFlawDiagnosis(
                flawTitle = "Open Racket Face",
                observedEffect = "Racket face opened by 14 degrees",
                rootCause = "Early shoulder rotation",
                coachingCue = "Keep racket head behind",
            ),
            actionItems = listOf("Focus on hip rotation"),
            recommendedDrills = listOf(
                DrillRecommendation(
                    drillType = DrillType.FOREHAND,
                    title = "Forehand basic drill",
                    focusPoint = "Hip rotation",
                    targetRepetitions = 20,
                ),
            ),
            isFallbackReport = false,
            rawModelName = LlmProvider.GEMINI.name,
        )

        assertEquals("r-123", report.reportId)
        assertEquals("s-456", report.sessionId)
        assertEquals(1_600_000_000L, report.generatedAtMillis)
        assertEquals("Good effort, but watch your racket face.", report.overallSummary)
        assertEquals(listOf("Fast swing speed"), report.keyStrengths)
        assertEquals("Open Racket Face", report.primaryFlawDiagnosis!!.flawTitle)
        assertEquals("Racket face opened by 14 degrees", report.primaryFlawDiagnosis!!.observedEffect)
        assertEquals("Early shoulder rotation", report.primaryFlawDiagnosis!!.rootCause)
        assertEquals("Keep racket head behind", report.primaryFlawDiagnosis!!.coachingCue)
        assertEquals(listOf("Focus on hip rotation"), report.actionItems)
        assertEquals(DrillType.FOREHAND, report.recommendedDrills.single().drillType)
        assertEquals(20, report.recommendedDrills.single().targetRepetitions)
        assertFalse(report.isFallbackReport)
        assertEquals("GEMINI", report.rawModelName)
    }

    @Test
    fun ac1_defaultsOmitOptionalFieldsAndUseTenRepetitions() {
        val report = AiCoachReport(
            reportId = "r-fallback",
            sessionId = "s-offline",
            generatedAtMillis = 1L,
            overallSummary = "Offline summary",
        )
        assertEquals(emptyList<String>(), report.keyStrengths)
        assertNull(report.primaryFlawDiagnosis)
        assertEquals(emptyList<String>(), report.actionItems)
        assertEquals(emptyList<DrillRecommendation>(), report.recommendedDrills)
        assertFalse(report.isFallbackReport)
        assertNull(report.rawModelName)

        val drill = DrillRecommendation(
            drillType = DrillType.SERVE,
            title = "Serve toss",
            focusPoint = "Stable toss",
        )
        assertEquals(10, drill.targetRepetitions)
    }

    @Test
    fun ac1_coachToneAndLlmProviderContainRequiredValues() {
        assertEquals(
            setOf("ENCOURAGING", "ANALYTICAL", "STRICT"),
            CoachTone.entries.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("MOCK", "GEMINI", "OPENAI", "LOCAL_RULE_ONLY"),
            LlmProvider.entries.map { it.name }.toSet(),
        )
        assertTrue(CoachTone.ENCOURAGING in CoachTone.entries)
        assertTrue(LlmProvider.MOCK in LlmProvider.entries)
    }
}
