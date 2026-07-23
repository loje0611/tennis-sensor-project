package com.example.swingsenseai.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

class ThemePreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    }

    val isDarkMode: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[Keys.IS_DARK_MODE] ?: true
    }

    suspend fun setDarkMode(isDark: Boolean) {
        context.themeDataStore.edit { it[Keys.IS_DARK_MODE] = isDark }
    }
}
