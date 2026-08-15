package io.github.loje0611.tennisdoc.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabDomainModelTest {

    @Test
    fun sessionTypeContainsMatchAndLab() {
        assertEquals(setOf("MATCH", "LAB"), SessionType.entries.map { it.name }.toSet())
    }

    @Test
    fun drillTypeContainsRequiredValues() {
        val names = DrillType.entries.map { it.name }.toSet()
        assertEquals(
            setOf(
                "FOREHAND",
                "BACKHAND",
                "SERVE",
                "FOREHAND_VOLLEY",
                "BACKHAND_VOLLEY",
            ),
            names,
        )
    }

    @Test
    fun labRawSwingRecordHoldsDrillAndPayloads() {
        val record = LabRawSwingRecord(
            id = 9L,
            sessionId = "lab-session",
            drillType = DrillType.FOREHAND,
            timestampMillis = 1_700_000_000_000L,
            imuRawJson = """[{"ax":1}]""",
            visionPosesJson = """[{"landmarks":[]}]""",
            impactOffsetMs = 40L,
        )
        assertEquals("lab-session", record.sessionId)
        assertEquals(DrillType.FOREHAND, record.drillType)
        assertEquals("""[{"ax":1}]""", record.imuRawJson)
        assertEquals("""[{"landmarks":[]}]""", record.visionPosesJson)
        assertEquals(40L, record.impactOffsetMs)
        assertTrue(record.id == 9L)
    }
}
