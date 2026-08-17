package io.github.loje0611.tennisdoc.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.loje0611.tennisdoc.core.data.repository.AiCoachPreferencesRepository
import io.github.loje0611.tennisdoc.core.data.repository.ThemePreferencesRepository
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.LlmProvider
import io.github.loje0611.tennisdoc.core.ui.theme.TennisDocTheme
import io.github.loje0611.tennisdoc.ui.settings.AiCoachSettingsSection
import io.github.loje0611.tennisdoc.ui.settings.ApiKeyTestStatus
import io.github.loje0611.tennisdoc.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AiCoachSettingsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    private val fakePrefs = object : AiCoachPreferencesRepository {
        override val geminiApiKey = MutableStateFlow<String?>(null)
        override val llmProvider = MutableStateFlow(LlmProvider.GEMINI)
        override val defaultCoachTone = MutableStateFlow(CoachTone.ENCOURAGING)
        override suspend fun setGeminiApiKey(apiKey: String?) {
            geminiApiKey.value = apiKey?.takeIf { it.isNotBlank() }
        }
        override suspend fun setLlmProvider(provider: LlmProvider) {
            llmProvider.value = provider
        }
        override suspend fun setDefaultCoachTone(tone: CoachTone) {
            defaultCoachTone.value = tone
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        val app = RuntimeEnvironment.getApplication()
        return SettingsViewModel(
            app,
            ThemePreferencesRepository(app),
            fakePrefs,
        )
    }

    @Test
    fun ac2_settingsSectionRendersProviderKeyTestButtonAndToneSelector() {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TennisDocTheme(isDarkMode = false) {
                AiCoachSettingsSection(viewModel)
            }
        }
        composeTestRule.onNodeWithText("🤖 AI 코치 설정").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Gemini Flash (권장)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gemini API Key").assertIsDisplayed()
        composeTestRule.onNodeWithText("연결 테스트").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google AI Studio에서 무료로 발급받은 API Key를 입력하세요.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("🌱 격려형").assertIsDisplayed()
        composeTestRule.onNodeWithText("📊 분석형").assertIsDisplayed()
        composeTestRule.onNodeWithText("🎯 엄격형").assertIsDisplayed()
    }

    @Test
    fun ac3_testGeminiApiKeyTransitionsTestingThenSuccessOrError() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        assertEquals(ApiKeyTestStatus.Idle, viewModel.apiKeyTestState.value)

        viewModel.testGeminiApiKey("AIza-valid-looking")
        testDispatcher.scheduler.runCurrent()
        assertEquals(ApiKeyTestStatus.Testing, viewModel.apiKeyTestState.value)

        advanceTimeBy(1_000L)
        advanceUntilIdle()
        assertEquals(ApiKeyTestStatus.Success, viewModel.apiKeyTestState.value)

        viewModel.testGeminiApiKey("not-a-key")
        testDispatcher.scheduler.runCurrent()
        assertEquals(ApiKeyTestStatus.Testing, viewModel.apiKeyTestState.value)
        advanceTimeBy(1_000L)
        advanceUntilIdle()
        val error = viewModel.apiKeyTestState.value
        assertTrue(error is ApiKeyTestStatus.Error)
        assertEquals("유효하지 않은 API Key 형식입니다.", (error as ApiKeyTestStatus.Error).message)
    }

    @Test
    fun ac4_toneChangeIsPersistedToPreferencesImmediately() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.saveDefaultCoachTone(CoachTone.ANALYTICAL)
        advanceUntilIdle()
        assertEquals(CoachTone.ANALYTICAL, fakePrefs.defaultCoachTone.value)

        viewModel.saveDefaultCoachTone(CoachTone.STRICT)
        advanceUntilIdle()
        assertEquals(CoachTone.STRICT, fakePrefs.defaultCoachTone.value)
    }

    @Test
    fun ac4_toneSelectorClickPersistsStrict() {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TennisDocTheme(isDarkMode = false) {
                AiCoachSettingsSection(viewModel)
            }
        }
        composeTestRule.onNodeWithText("🎯 엄격형").performClick()
        composeTestRule.waitForIdle()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(CoachTone.STRICT, fakePrefs.defaultCoachTone.value)
    }
}
