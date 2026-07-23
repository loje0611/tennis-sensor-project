package com.example.swingsenseai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.swingsenseai.data.repository.ThemePreferencesRepository
import com.example.swingsenseai.navigation.AppNavHost
import com.example.swingsenseai.ui.theme.SwingSenseAITheme
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
