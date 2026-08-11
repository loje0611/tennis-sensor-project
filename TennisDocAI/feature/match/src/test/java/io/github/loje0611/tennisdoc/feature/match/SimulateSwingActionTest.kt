package io.github.loje0611.tennisdoc.feature.match

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulateSwingActionTest {

    @Test
    fun `debug off ignores regardless of pipeline`() {
        assertEquals(
            SimulateSwingAction.Ignore,
            resolveSimulateSwingAction(debugModeEnabled = false, pipelineRunning = false),
        )
        assertEquals(
            SimulateSwingAction.Ignore,
            resolveSimulateSwingAction(debugModeEnabled = false, pipelineRunning = true),
        )
    }

    @Test
    fun `debug on and pipeline running requests service simulation`() {
        assertEquals(
            SimulateSwingAction.RequestServiceSimulation,
            resolveSimulateSwingAction(debugModeEnabled = true, pipelineRunning = true),
        )
    }

    @Test
    fun `debug on and pipeline idle updates label directly`() {
        assertEquals(
            SimulateSwingAction.UpdateLabelDirectly,
            resolveSimulateSwingAction(debugModeEnabled = true, pipelineRunning = false),
        )
    }
}
