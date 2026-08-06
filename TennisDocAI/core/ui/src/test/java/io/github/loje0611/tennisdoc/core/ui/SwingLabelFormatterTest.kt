package io.github.loje0611.tennisdoc.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SwingLabelFormatterTest {

    @Test
    fun testLinesForDisplayEmpty() {
        assertEquals(listOf("—"), SwingLabelFormatter.linesForDisplay(""))
        assertEquals(listOf("—"), SwingLabelFormatter.linesForDisplay("   "))
    }

    @Test
    fun testLinesForDisplayUnderscore() {
        assertEquals(listOf("Forehand", "Topspin"), SwingLabelFormatter.linesForDisplay("Forehand_topspin"))
        assertEquals(listOf("Backhand", "Slice"), SwingLabelFormatter.linesForDisplay("Backhand_Slice"))
    }

    @Test
    fun testLinesForDisplayCamelCase() {
        assertEquals(listOf("Forehand", "Topspin"), SwingLabelFormatter.linesForDisplay("ForehandTopspin"))
        assertEquals(listOf("Idle"), SwingLabelFormatter.linesForDisplay("Idle"))
    }

    @Test
    fun testPhraseForTtsOnly() {
        assertEquals("", SwingLabelFormatter.phraseForTtsOnly(""))
        assertEquals("Topspin", SwingLabelFormatter.phraseForTtsOnly("Forehand_Topspin"))
        assertEquals("Slice", SwingLabelFormatter.phraseForTtsOnly("Backhand_Slice"))
        assertEquals("Volley", SwingLabelFormatter.phraseForTtsOnly("Forehand_Volley"))
        assertEquals("Idle", SwingLabelFormatter.phraseForTtsOnly("Idle"))
    }
}
