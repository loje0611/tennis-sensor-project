package io.github.loje0611.tennisdoc.settings

import io.github.loje0611.tennisdoc.core.data.repository.VideoPreferencesRepositoryImpl
import io.github.loje0611.tennisdoc.core.model.VideoRetentionOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class VideoPreferencesRepositoryTest {

    private lateinit var repository: VideoPreferencesRepositoryImpl

    @Before
    fun setUp() {
        repository = VideoPreferencesRepositoryImpl(RuntimeEnvironment.getApplication())
    }

    @Test
    fun a_defaultsAreAutoSaveEnabledAndCount50WhenUnset() = runTest {
        assertTrue(repository.autoSaveVideoEnabled.first())
        assertEquals(VideoRetentionOption.COUNT_50, repository.videoRetentionOption.first())
    }

    @Test
    fun b_persistsAutoSaveAndRetentionRoundTrip() = runTest {
        repository.setAutoSaveVideoEnabled(false)
        repository.setVideoRetentionOption(VideoRetentionOption.COUNT_20)
        assertEquals(false, repository.autoSaveVideoEnabled.first())
        assertEquals(VideoRetentionOption.COUNT_20, repository.videoRetentionOption.first())

        repository.setAutoSaveVideoEnabled(true)
        repository.setVideoRetentionOption(VideoRetentionOption.COUNT_50)
        assertTrue(repository.autoSaveVideoEnabled.first())
        assertEquals(VideoRetentionOption.COUNT_50, repository.videoRetentionOption.first())

        repository.setVideoRetentionOption(VideoRetentionOption.UNLIMITED)
        assertEquals(VideoRetentionOption.UNLIMITED, repository.videoRetentionOption.first())
        assertTrue(repository.autoSaveVideoEnabled.first())
    }
}
