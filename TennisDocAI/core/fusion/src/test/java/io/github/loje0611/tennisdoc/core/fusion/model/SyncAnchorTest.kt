package io.github.loje0611.tennisdoc.core.fusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAnchorTest {

    @Test
    fun `timeOffsetMs is correctly calculated as sensor minus vision timestamp`() {
        val anchor = SyncAnchor(
            visionImpactTimestampMs = 1000L,
            sensorImpactTimestampMs = 1050L,
            confidence = 0.95f
        )

        assertEquals(50L, anchor.timeOffsetMs)
        assertTrue(anchor.isSynchronized)
        assertEquals(0.95f, anchor.confidence, 0.001f)
    }

    @Test
    fun `negative timeOffsetMs within threshold is synchronized`() {
        val anchor = SyncAnchor(
            visionImpactTimestampMs = 1080L,
            sensorImpactTimestampMs = 1000L,
            confidence = 0.88f
        )

        assertEquals(-80L, anchor.timeOffsetMs)
        assertTrue(anchor.isSynchronized)
    }

    @Test
    fun `timeOffsetMs at boundary 100ms is synchronized`() {
        val anchorPos = SyncAnchor(
            visionImpactTimestampMs = 1000L,
            sensorImpactTimestampMs = 1100L,
            confidence = 0.90f
        )
        val anchorNeg = SyncAnchor(
            visionImpactTimestampMs = 1100L,
            sensorImpactTimestampMs = 1000L,
            confidence = 0.90f
        )

        assertEquals(100L, anchorPos.timeOffsetMs)
        assertTrue(anchorPos.isSynchronized)

        assertEquals(-100L, anchorNeg.timeOffsetMs)
        assertTrue(anchorNeg.isSynchronized)
    }

    @Test
    fun `timeOffsetMs exceeding threshold 100ms is not synchronized`() {
        val anchorPos = SyncAnchor(
            visionImpactTimestampMs = 1000L,
            sensorImpactTimestampMs = 1101L,
            confidence = 0.60f
        )
        val anchorNeg = SyncAnchor(
            visionImpactTimestampMs = 1101L,
            sensorImpactTimestampMs = 1000L,
            confidence = 0.60f
        )

        assertEquals(101L, anchorPos.timeOffsetMs)
        assertFalse(anchorPos.isSynchronized)

        assertEquals(-101L, anchorNeg.timeOffsetMs)
        assertFalse(anchorNeg.isSynchronized)
    }
}
