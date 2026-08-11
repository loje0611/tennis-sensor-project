package io.github.loje0611.tennisdoc.feature.match

import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class MatchViewModelTest {

    private lateinit var port: FakeMatchSessionPort
    private lateinit var viewModel: MatchViewModel

    @Before
    fun setup() {
        port = FakeMatchSessionPort()
        viewModel = MatchViewModel(port)
    }

    @Test
    fun `exposes port state flows`() {
        port.setConnectionState(BleConnectionState.Connected)
        port.setDetectedSwingLabel("forehand_topspin")
        port.setSwingCount(3)
        port.setSessionDurationSeconds(42L)
        port.setDebugModeEnabled(true)

        assertSame(port.connectionState, viewModel.connectionState)
        assertEquals(BleConnectionState.Connected, viewModel.connectionState.value)
        assertEquals("forehand_topspin", viewModel.detectedSwingLabel.value)
        assertEquals(3, viewModel.swingCount.value)
        assertEquals(42L, viewModel.sessionDurationSeconds.value)
        assertEquals(true, viewModel.isDebugModeEnabled.value)
    }

    @Test
    fun `scanAndConnect delegates to port`() {
        viewModel.scanAndConnect()
        assertEquals(1, port.scanAndConnectCalls)
    }

    @Test
    fun `disconnect delegates to port`() {
        viewModel.disconnect()
        assertEquals(1, port.disconnectCalls)
    }

    @Test
    fun `onDebugActivationAreaTap delegates to port`() {
        viewModel.onDebugActivationAreaTap()
        viewModel.onDebugActivationAreaTap()
        assertEquals(2, port.debugActivationTapCalls)
    }

    @Test
    fun `simulateSwing delegates type to port`() {
        viewModel.simulateSwing("backhand_slice")
        assertEquals(listOf("backhand_slice"), port.simulatedSwingTypes)
    }
}
