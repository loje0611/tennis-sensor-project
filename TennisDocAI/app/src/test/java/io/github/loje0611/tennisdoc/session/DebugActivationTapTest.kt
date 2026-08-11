package io.github.loje0611.tennisdoc.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebugActivationTapTest {

    @Before
    fun reset() {
        SwingAnalysisSessionState.resetSessionUiState()
    }

    @Test
    fun `nine taps leave debug mode off`() {
        repeat(SwingAnalysisSessionState.DEBUG_ACTIVATION_TAP_THRESHOLD - 1) {
            SwingAnalysisSessionState.onDebugActivationAreaTap()
        }
        assertFalse(SwingAnalysisSessionState.debugModeEnabled.value)
    }

    @Test
    fun `tenth tap enables debug mode`() {
        repeat(SwingAnalysisSessionState.DEBUG_ACTIVATION_TAP_THRESHOLD) {
            SwingAnalysisSessionState.onDebugActivationAreaTap()
        }
        assertTrue(SwingAnalysisSessionState.debugModeEnabled.value)
    }

    @Test
    fun `additional taps after enabled do nothing`() {
        SwingAnalysisSessionState.setDebugMode(true)
        repeat(20) {
            SwingAnalysisSessionState.onDebugActivationAreaTap()
        }
        assertTrue(SwingAnalysisSessionState.debugModeEnabled.value)
    }

    @Test
    fun `threshold is ten and shared constant`() {
        assertEquals(10, SwingAnalysisSessionState.DEBUG_ACTIVATION_TAP_THRESHOLD)
    }

    @Test
    fun `disabling debug resets tap progress`() {
        repeat(5) {
            SwingAnalysisSessionState.onDebugActivationAreaTap()
        }
        SwingAnalysisSessionState.setDebugMode(false)
        repeat(9) {
            SwingAnalysisSessionState.onDebugActivationAreaTap()
        }
        assertFalse(SwingAnalysisSessionState.debugModeEnabled.value)
        SwingAnalysisSessionState.onDebugActivationAreaTap()
        assertTrue(SwingAnalysisSessionState.debugModeEnabled.value)
    }
}
