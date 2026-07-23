package com.example.swingsenseai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class SwingColorScheme(
    val background: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val subGray: Color,
    
    val scanningTrack: Color,
    val progressTrack: Color,
    
    val dotDisconnected: Color,
    val dotScanning: Color,
    val dotConnected: Color,
    
    val neonGreenTopspin: Color,
    val electricCyanSlice: Color,
    val vividNeonOrangeFlat: Color,
    val neonPurpleSettings: Color,
    val neonVolley: Color,
    val connectBlue: Color,
    
    val stopBg: Color,
    val stopTextRed: Color,
    val danger: Color,
    val success: Color,
    val warningChipBg: Color,
    val successChipBg: Color,
    val warningChipFg: Color,
    val successChipFg: Color,
    
    val brushTopspin: Brush,
    val brushSlice: Brush,
    val brushFlat: Brush,
    val brushVolley: Brush,
    val brushConnectButton: Brush,
    val brushStopButton: Brush,
)

val DarkSwingColors = SwingColorScheme(
    background = Color(0xFF030305),
    onBackground = Color(0xFFFFFFFF),
    onBackgroundVariant = Color.LightGray,
    cardSurface = Color(0xFF111116),
    cardBorder = Color(0xFF2C2C35),
    subGray = Color(0xFF888891),
    
    scanningTrack = Color(0xFF1E1E24),
    progressTrack = Color(0xFF15151A),
    
    dotDisconnected = Color(0xFF55555C),
    dotScanning = Color(0xFFFFCC00),
    dotConnected = Color(0xFF39FF14),
    
    neonGreenTopspin = Color(0xFF39FF14),
    electricCyanSlice = Color(0xFF00E5FF),
    vividNeonOrangeFlat = Color(0xFFFF5E00),
    neonPurpleSettings = Color(0xFFB026FF),
    neonVolley = Color(0xFFB026FF),
    connectBlue = Color(0xFF0055FF),
    
    stopBg = Color(0xFFCC0033),
    stopTextRed = Color(0xFFFFCCCC),
    danger = Color(0xFFFF3B30),
    success = Color(0xFF39FF14),
    warningChipBg = Color(0xFF2A2520),
    successChipBg = Color(0xFF2A1F35),
    warningChipFg = Color(0xFFFFB74D),
    successChipFg = Color(0xFFFF8A65),

    brushTopspin = Brush.horizontalGradient(listOf(Color(0xFF0F9B0F), Color(0xFF39FF14))),
    brushSlice = Brush.horizontalGradient(listOf(Color(0xFF0066FF), Color(0xFF00E5FF))),
    brushFlat = Brush.horizontalGradient(listOf(Color(0xFFB33200), Color(0xFFFF5E00))),
    brushVolley = Brush.horizontalGradient(listOf(Color(0xFF5C00A8), Color(0xFFB026FF), Color(0xFFFF55FF))),
    brushConnectButton = Brush.linearGradient(listOf(Color(0xFF0033CC), Color(0xFF0088FF))),
    brushStopButton = Brush.linearGradient(listOf(Color(0xFF990022), Color(0xFFFF3B30))),
)

val LightSwingColors = SwingColorScheme(
    background = Color(0xFFF5F5F7), // Light gray/off-white background
    onBackground = Color(0xFF1C1C1E), // Deep charcoal for texts
    onBackgroundVariant = Color.DarkGray,
    cardSurface = Color(0xFFFFFFFF), // Pure white cards
    cardBorder = Color(0xFFE5E5EA), // Soft border
    subGray = Color(0xFF888891),
    
    scanningTrack = Color(0xFFE5E5EA),
    progressTrack = Color(0xFFE0E0E0),
    
    dotDisconnected = Color(0xFFA1A1A6), // Lighter gray for disconnected
    dotScanning = Color(0xFFFFCC00),
    dotConnected = Color(0xFF34C759), // iOS style green for light mode
    
    // Neon colors are adjusted to bold solid colors suitable for Light Mode without looking muddy
    neonGreenTopspin = Color(0xFF2E8B57), // SeaGreen
    electricCyanSlice = Color(0xFF1E90FF), // DodgerBlue
    vividNeonOrangeFlat = Color(0xFFFF8C00), // DarkOrange
    neonPurpleSettings = Color(0xFF8A2BE2), // BlueViolet
    neonVolley = Color(0xFF8A2BE2),
    connectBlue = Color(0xFF007AFF), // iOS blue
    
    stopBg = Color(0xFFFF3B30),
    stopTextRed = Color(0xFFFFFFFF),
    danger = Color(0xFFFF3B30),
    success = Color(0xFF34C759),
    warningChipBg = Color(0xFFFFF3E0),
    successChipBg = Color(0xFFF3E5F5),
    warningChipFg = Color(0xFFE65100),
    successChipFg = Color(0xFF6A1B9A),

    brushTopspin = Brush.horizontalGradient(listOf(Color(0xFF228B22), Color(0xFF2E8B57))),
    brushSlice = Brush.horizontalGradient(listOf(Color(0xFF4169E1), Color(0xFF1E90FF))),
    brushFlat = Brush.horizontalGradient(listOf(Color(0xFFD2691E), Color(0xFFFF8C00))),
    brushVolley = Brush.horizontalGradient(listOf(Color(0xFF4B0082), Color(0xFF8A2BE2))),
    brushConnectButton = Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF34A0FF))),
    brushStopButton = Brush.linearGradient(listOf(Color(0xFFFF3B30), Color(0xFFFF6961))),
)

val LocalSwingColorScheme = staticCompositionLocalOf { DarkSwingColors }

object SwingTheme {
    val colors: SwingColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalSwingColorScheme.current
}
