package io.github.loje0611.tennisdoc.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.LlmProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AiCoachPreferencesRepository {
    val geminiApiKey: Flow<String?>
    val llmProvider: Flow<LlmProvider>
    val defaultCoachTone: Flow<CoachTone>
    suspend fun setGeminiApiKey(apiKey: String?)
    suspend fun setLlmProvider(provider: LlmProvider)
    suspend fun setDefaultCoachTone(tone: CoachTone)
}

private val Context.aiCoachDataStore by preferencesDataStore(name = "ai_coach_settings")

class AiCoachPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AiCoachPreferencesRepository {
    private object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val LLM_PROVIDER = stringPreferencesKey("llm_provider")
        val DEFAULT_COACH_TONE = stringPreferencesKey("default_coach_tone")
    }

    override val geminiApiKey: Flow<String?> = context.aiCoachDataStore.data.map { prefs ->
        prefs[Keys.GEMINI_API_KEY]?.takeIf { it.isNotBlank() }
    }

    override val llmProvider: Flow<LlmProvider> = context.aiCoachDataStore.data.map { prefs ->
        prefs[Keys.LLM_PROVIDER]?.let { runCatching { LlmProvider.valueOf(it) }.getOrNull() } ?: LlmProvider.GEMINI
    }

    override val defaultCoachTone: Flow<CoachTone> = context.aiCoachDataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_COACH_TONE]?.let { runCatching { CoachTone.valueOf(it) }.getOrNull() } ?: CoachTone.ENCOURAGING
    }

    override suspend fun setGeminiApiKey(apiKey: String?) {
        context.aiCoachDataStore.edit { prefs ->
            if (apiKey.isNullOrBlank()) {
                prefs.remove(Keys.GEMINI_API_KEY)
            } else {
                prefs[Keys.GEMINI_API_KEY] = apiKey
            }
        }
    }

    override suspend fun setLlmProvider(provider: LlmProvider) {
        context.aiCoachDataStore.edit { prefs ->
            prefs[Keys.LLM_PROVIDER] = provider.name
        }
    }

    override suspend fun setDefaultCoachTone(tone: CoachTone) {
        context.aiCoachDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_COACH_TONE] = tone.name
        }
    }
}
