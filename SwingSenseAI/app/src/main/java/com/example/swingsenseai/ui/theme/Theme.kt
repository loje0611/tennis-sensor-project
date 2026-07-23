package com.example.swingsenseai.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SwingSenseDarkScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color(0xFF0A1F44),
    secondary = ElectricBlue,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = ElectricBlueDark,
    onTertiary = Color(0xFFFFFFFF),
    background = NavyDeep,
    onBackground = TextPrimaryLight,
    surface = NavySurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFF3D5A80),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF0A1F44),
)

@Composable
fun SwingSenseAITheme(
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val swingColors = if (isDarkMode) DarkSwingColors else LightSwingColors
    // Keep material dark scheme for fallback components, we mostly use swingColors anyway
    val materialColorScheme = SwingSenseDarkScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = swingColors.background.toArgb()
            window.navigationBarColor = swingColors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkMode
                isAppearanceLightNavigationBars = !isDarkMode
            }
        }
    }

    CompositionLocalProvider(LocalSwingColorScheme provides swingColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
