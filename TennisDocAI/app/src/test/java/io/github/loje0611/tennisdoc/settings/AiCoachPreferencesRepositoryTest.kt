package io.github.loje0611.tennisdoc.settings

import io.github.loje0611.tennisdoc.core.data.repository.AiCoachPreferencesRepositoryImpl
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.LlmProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AiCoachPreferencesRepositoryTest {

    private lateinit var repository: AiCoachPreferencesRepositoryImpl

    @Before
    fun setUp() {
        repository = AiCoachPreferencesRepositoryImpl(RuntimeEnvironment.getApplication())
    }

    @Test
    fun ac1_persistsApiKeyProviderAndCoachToneRoundTrip() = runTest {
        repository.setGeminiApiKey("AIza-roundtrip-key")
        repository.setLlmProvider(LlmProvider.MOCK)
        repository.setDefaultCoachTone(CoachTone.STRICT)

        assertEquals("AIza-roundtrip-key", repository.geminiApiKey.first())
        assertEquals(LlmProvider.MOCK, repository.llmProvider.first())
        assertEquals(CoachTone.STRICT, repository.defaultCoachTone.first())

        repository.setGeminiApiKey("   ")
        assertNull(repository.geminiApiKey.first())
        assertEquals(LlmProvider.MOCK, repository.llmProvider.first())
        assertEquals(CoachTone.STRICT, repository.defaultCoachTone.first())
    }

    @Test
    fun ac1_blankApiKeyIsStoredAsNullAndDefaultsAreGeminiEncouraging() = runTest {
        repository.setGeminiApiKey(null)
        repository.setLlmProvider(LlmProvider.GEMINI)
        repository.setDefaultCoachTone(CoachTone.ENCOURAGING)

        assertNull(repository.geminiApiKey.first())
        assertEquals(LlmProvider.GEMINI, repository.llmProvider.first())
        assertEquals(CoachTone.ENCOURAGING, repository.defaultCoachTone.first())
    }
}
