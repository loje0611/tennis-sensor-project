package io.github.loje0611.tennisdoc.session

import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import io.github.loje0611.tennisdoc.service.SwingAnalysisForegroundService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

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
        val sid = port.startSession(SessionType.LAB, DrillType.FOREHAND_VOLLEY)

        assertEquals(sid, SwingAnalysisSessionState.activeSessionId.value)
        assertEquals(SessionType.LAB, SwingAnalysisSessionState.activeSessionType.value)
        assertEquals(DrillType.FOREHAND_VOLLEY, SwingAnalysisSessionState.activeDrillType.value)
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

    @Test
    fun ac11_connectSensorWhileIdleDoesNotStartSessionRunningForegroundService() {
        val app = RuntimeEnvironment.getApplication()
        val shadowApp = Shadows.shadowOf(app)
        drainStartedServices(shadowApp)

        val port = LabSessionPortImpl(app)
        assertFalse(port.isSessionActive.value)
        port.connectSensor()

        val started = shadowApp.nextStartedService
        assertNotNull(started)
        assertNotEquals(
            "idle connect must not start the session-running foreground notification",
            SwingAnalysisForegroundService.ACTION_START,
            started!!.action,
        )
    }

    @Test
    fun ac11_startSessionStartsForegroundNotificationAndFinishStopsIt() {
        val app = RuntimeEnvironment.getApplication()
        val shadowApp = Shadows.shadowOf(app)
        drainStartedServices(shadowApp)

        val port = LabSessionPortImpl(app)
        port.startSession(SessionType.LAB, DrillType.FOREHAND)

        val started = shadowApp.nextStartedService
        assertNotNull(started)
        assertEquals(SwingAnalysisForegroundService.ACTION_START, started!!.action)

        drainStartedServices(shadowApp)
        port.finishSession()

        val stopped = shadowApp.nextStartedService
        assertNotNull(stopped)
        assertEquals(SwingAnalysisForegroundService.ACTION_STOP, stopped!!.action)
    }

    private fun drainStartedServices(shadowApp: ShadowApplication) {
        while (shadowApp.peekNextStartedService() != null) {
            shadowApp.nextStartedService
        }
    }
}
