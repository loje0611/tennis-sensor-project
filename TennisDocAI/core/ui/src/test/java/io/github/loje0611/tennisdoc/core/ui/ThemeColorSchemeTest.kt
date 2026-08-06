package io.github.loje0611.tennisdoc.core.ui

import io.github.loje0611.tennisdoc.core.ui.theme.DarkSwingColors
import io.github.loje0611.tennisdoc.core.ui.theme.LightSwingColors
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeColorSchemeTest {

    @Test
    fun testDarkAndLightColorsAreDistinctInstances() {
        assertNotEquals(DarkSwingColors, LightSwingColors)
    }

    @Test
    fun testColorSchemeFieldsAreInitialized() {
        listOf(DarkSwingColors, LightSwingColors).forEach { scheme ->
            assertNotNull(scheme.background)
            assertNotNull(scheme.onBackground)
            assertNotNull(scheme.onBackgroundVariant)
            assertNotNull(scheme.cardSurface)
            assertNotNull(scheme.cardBorder)
            assertNotNull(scheme.subGray)

            assertNotNull(scheme.scanningTrack)
            assertNotNull(scheme.progressTrack)

            assertNotNull(scheme.dotDisconnected)
            assertNotNull(scheme.dotScanning)
            assertNotNull(scheme.dotConnected)

            assertNotNull(scheme.neonGreenTopspin)
            assertNotNull(scheme.electricCyanSlice)
            assertNotNull(scheme.vividNeonOrangeFlat)
            assertNotNull(scheme.neonPurpleSettings)
            assertNotNull(scheme.neonVolley)
            assertNotNull(scheme.connectBlue)

            assertNotNull(scheme.stopBg)
            assertNotNull(scheme.stopTextRed)
            assertNotNull(scheme.danger)
            assertNotNull(scheme.success)
            assertNotNull(scheme.warningChipBg)
            assertNotNull(scheme.successChipBg)
            assertNotNull(scheme.warningChipFg)
            assertNotNull(scheme.successChipFg)

            assertNotNull(scheme.brushTopspin)
            assertNotNull(scheme.brushSlice)
            assertNotNull(scheme.brushFlat)
            assertNotNull(scheme.brushVolley)
            assertNotNull(scheme.brushConnectButton)
            assertNotNull(scheme.brushStopButton)
        }
    }
}
