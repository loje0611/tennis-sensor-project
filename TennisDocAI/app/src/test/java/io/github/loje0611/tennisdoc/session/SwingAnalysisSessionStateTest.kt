package io.github.loje0611.tennisdoc.session

import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SwingAnalysisSessionStateTest {

    @Before
    fun reset() {
        SwingAnalysisSessionState.resetSessionUiState()
    }

    @Test
    fun `updateConnection changes connection state`() {
        SwingAnalysisSessionState.updateConnection(BleConnectionState.Scanning)
        assertEquals(BleConnectionState.Scanning, SwingAnalysisSessionState.connectionState.value)
        SwingAnalysisSessionState.updateConnection(BleConnectionState.Connected)
        assertTrue(SwingAnalysisSessionState.connectionState.value.isConnected)
    }

    @Test
    fun `incrementSwingCount updates total and breakdown`() {
        SwingAnalysisSessionState.incrementSwingCount("Forehand_Topspin")
        SwingAnalysisSessionState.incrementSwingCount("forehand_topspin")
        assertEquals(2, SwingAnalysisSessionState.swingCount.value)
        // normalize: underscore → space, lowercase → "forehand topspin"
        assertEquals(2, SwingAnalysisSessionState.swingBreakdown.value["forehand topspin"])
    }

    @Test
    fun `updateSessionDuration sets duration seconds`() {
        SwingAnalysisSessionState.updateSessionDuration(15L)
        assertEquals(15L, SwingAnalysisSessionState.sessionDurationSeconds.value)
    }

    @Test
    fun `resetSessionUiState clears counters and debug`() {
        SwingAnalysisSessionState.updateConnection(BleConnectionState.Connected)
        SwingAnalysisSessionState.incrementSwingCount("slice")
        SwingAnalysisSessionState.updateSessionDuration(9L)
        SwingAnalysisSessionState.setDebugMode(true)

        SwingAnalysisSessionState.resetSessionUiState()

        assertEquals(BleConnectionState.Disconnected, SwingAnalysisSessionState.connectionState.value)
        assertEquals(0, SwingAnalysisSessionState.swingCount.value)
        assertEquals(0L, SwingAnalysisSessionState.sessionDurationSeconds.value)
        assertFalse(SwingAnalysisSessionState.debugModeEnabled.value)
        assertFalse(SwingAnalysisSessionState.isPipelineRunning())
    }

    @Test
    fun `pipeline running flag toggles`() {
        assertFalse(SwingAnalysisSessionState.isPipelineRunning())
        SwingAnalysisSessionState.setPipelineRunning(true)
        assertTrue(SwingAnalysisSessionState.isPipelineRunning())
        SwingAnalysisSessionState.setPipelineRunning(false)
        assertFalse(SwingAnalysisSessionState.isPipelineRunning())
    }
}
