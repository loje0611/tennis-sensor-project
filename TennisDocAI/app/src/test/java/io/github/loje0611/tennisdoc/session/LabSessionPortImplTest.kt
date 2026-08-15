package io.github.loje0611.tennisdoc.session

import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LabSessionPortImplTest {

    @Before
    fun setUp() {
        SwingAnalysisSessionState.resetSessionUiState()
    }

    @After
    fun tearDown() {
        SwingAnalysisSessionState.resetSessionUiState()
    }

    @Test
    fun startSessionForwardsLabTypeAndSelectedDrill() {
        val port = LabSessionPortImpl(RuntimeEnvironment.getApplication())
        val sid = port.startSession(SessionType.LAB, DrillType.VOLLEY)

        assertEquals(sid, SwingAnalysisSessionState.activeSessionId.value)
        assertEquals(SessionType.LAB, SwingAnalysisSessionState.activeSessionType.value)
        assertEquals(DrillType.VOLLEY, SwingAnalysisSessionState.activeDrillType.value)
        assertTrue(port.isSessionActive.value)

        port.finishSession()
        assertFalse(port.isSessionActive.value)
        assertFalse(SwingAnalysisSessionState.isSessionActive.value)
    }

    @Test
    fun isDebugModeEnabledReflectsSessionState() {
        val port = LabSessionPortImpl(RuntimeEnvironment.getApplication())
        assertFalse(port.isDebugModeEnabled.value)

        SwingAnalysisSessionState.setDebugMode(true)
        assertTrue(port.isDebugModeEnabled.value)

        SwingAnalysisSessionState.setDebugMode(false)
        assertFalse(port.isDebugModeEnabled.value)
    }
}
