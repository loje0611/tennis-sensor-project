package io.github.loje0611.tennisdoc.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.loje0611.tennisdoc.core.data.repository.AiCoachPreferencesRepository
import io.github.loje0611.tennisdoc.core.data.repository.ThemePreferencesRepository
import io.github.loje0611.tennisdoc.core.data.repository.VideoFileManager
import io.github.loje0611.tennisdoc.core.data.repository.VideoPreferencesRepository
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.LlmProvider
import io.github.loje0611.tennisdoc.core.model.VideoRetentionOption
import io.github.loje0611.tennisdoc.core.ui.theme.TennisDocTheme
import io.github.loje0611.tennisdoc.ui.settings.SettingsViewModel
import io.github.loje0611.tennisdoc.ui.settings.VideoStorageSettingsSection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VideoSettingsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    private val fakeAiPrefs = object : AiCoachPreferencesRepository {
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

    private val fakeVideoPrefs = object : VideoPreferencesRepository {
        override val autoSaveVideoEnabled = MutableStateFlow(true)
        override val videoRetentionOption = MutableStateFlow(VideoRetentionOption.COUNT_50)
        override suspend fun setAutoSaveVideoEnabled(enabled: Boolean) {
            autoSaveVideoEnabled.value = enabled
        }
        override suspend fun setVideoRetentionOption(option: VideoRetentionOption) {
            videoRetentionOption.value = option
        }
    }

    private val fakeVideoFileManager = object : VideoFileManager {
        var lastEnforceMaxCount: Int? = null
        var clearAllCalls = 0
        override fun getVideoDirectory() = File("")
        override fun generateVideoFile(sessionId: String, recordId: Long) = File("")
        override fun getUsedStorageBytes() = 0L
        override fun formatStorageSize(bytes: Long) = "0 MB"
        override suspend fun deleteVideoFile(filePath: String) = true
        override suspend fun clearAllVideos(): Int {
            clearAllCalls += 1
            return 0
        }
        override suspend fun enforceRetentionPolicy(maxCount: Int): Int {
            lastEnforceMaxCount = maxCount
            return 0
        }
    }

    private val fakeLabRawRecordDao = object : io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao {
        override suspend fun insert(record: io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity) = 0L
        override fun getRecordsBySessionId(sessionId: String) =
            flowOf(emptyList<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>())
        override suspend fun getRecordById(id: Long) = null
        override suspend fun deleteRecordsBySessionId(sessionId: String) = 0
        override suspend fun updateVideoPath(id: Long, videoPath: String?) {}
        override suspend fun getRecordsWithVideoAsc() =
            emptyList<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>()
        override fun observeVideoRecordCount() = flowOf(0)
        override suspend fun clearVideoPathByPath(videoPath: String) {}
        override suspend fun clearAllVideoPaths() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeVideoPrefs.autoSaveVideoEnabled.value = true
        fakeVideoPrefs.videoRetentionOption.value = VideoRetentionOption.COUNT_50
        fakeVideoFileManager.lastEnforceMaxCount = null
        fakeVideoFileManager.clearAllCalls = 0
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
            fakeAiPrefs,
            fakeVideoPrefs,
            fakeVideoFileManager,
            fakeLabRawRecordDao,
        )
    }

    @Test
    fun cardRendersToggleRetentionDropdownAndClearButton() {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TennisDocTheme(isDarkMode = false) {
                VideoStorageSettingsSection(viewModel)
            }
        }
        composeTestRule.onNodeWithText("📹 스윙 영상 & 저장소 설정").assertIsDisplayed()
        composeTestRule.onNodeWithText("스윙 영상 자동 저장").assertIsDisplayed()
        composeTestRule.onNodeWithText("스윙 감지 시 2초 비디오 클립을 저장합니다.").assertIsDisplayed()
        composeTestRule.onNodeWithText("최근 50개 (권장) (약 25 MB)").assertIsDisplayed()
        composeTestRule.onNodeWithText("저장된 비디오: 0 개 / 0 MB").assertIsDisplayed()
        composeTestRule.onNodeWithText("🗑️ 비디오 캐시 전체 삭제").assertIsDisplayed()
        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun toggleAutoSaveUpdatesImmediately() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TennisDocTheme(isDarkMode = false) {
                VideoStorageSettingsSection(viewModel)
            }
        }
        composeTestRule.onNode(isToggleable()).assertIsOn()
        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.waitForIdle()
        advanceUntilIdle()
        assertEquals(false, fakeVideoPrefs.autoSaveVideoEnabled.value)
        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun retentionDropdownChangeAppliesImmediatelyAndEnforcesPolicy() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TennisDocTheme(isDarkMode = false) {
                VideoStorageSettingsSection(viewModel)
            }
        }
        composeTestRule.onNodeWithText("최근 50개 (권장) (약 25 MB)").performClick()
        composeTestRule.onNodeWithText("최근 20개 (약 10 MB)").performClick()
        composeTestRule.waitForIdle()
        advanceUntilIdle()
        assertEquals(VideoRetentionOption.COUNT_20, fakeVideoPrefs.videoRetentionOption.value)
        assertEquals(20, fakeVideoFileManager.lastEnforceMaxCount)
        composeTestRule.onNodeWithText("최근 20개 (약 10 MB)").assertIsDisplayed()
    }

    @Test
    fun clearCacheDialogConfirmDeletesAndShowsToast() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TennisDocTheme(isDarkMode = false) {
                VideoStorageSettingsSection(viewModel)
            }
        }
        composeTestRule.onNodeWithText("🗑️ 비디오 캐시 전체 삭제").performClick()
        composeTestRule.onNodeWithText("비디오 캐시 삭제").assertIsDisplayed()
        composeTestRule.onNodeWithText("삭제").performClick()
        composeTestRule.waitForIdle()
        advanceUntilIdle()
        assertEquals(1, fakeVideoFileManager.clearAllCalls)
        assertEquals("스윙 영상 캐시가 모두 삭제되었습니다.", ShadowToast.getTextOfLatestToast())
    }
}
