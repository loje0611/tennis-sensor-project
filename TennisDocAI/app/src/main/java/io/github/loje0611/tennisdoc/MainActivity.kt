package io.github.loje0611.tennisdoc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.loje0611.tennisdoc.data.repository.ThemePreferencesRepository
import io.github.loje0611.tennisdoc.navigation.AppNavHost
import io.github.loje0611.tennisdoc.ui.theme.SwingSenseAITheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themePreferences: ThemePreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = true)

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkMode)
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (isDarkMode)
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }

            SwingSenseAITheme(isDarkMode = isDarkMode) {
                AppNavHost()
            }
        }
    }
}
