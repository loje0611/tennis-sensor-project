package io.github.loje0611.tennisdoc.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.loje0611.tennisdoc.core.model.VideoRetentionOption
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface VideoPreferencesRepository {
    val autoSaveVideoEnabled: Flow<Boolean>
    val videoRetentionOption: Flow<VideoRetentionOption>
    suspend fun setAutoSaveVideoEnabled(enabled: Boolean)
    suspend fun setVideoRetentionOption(option: VideoRetentionOption)
}

private val Context.videoPreferencesDataStore by preferencesDataStore(name = "video_preferences")

class VideoPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : VideoPreferencesRepository {
    private object Keys {
        val AUTO_SAVE_VIDEO = booleanPreferencesKey("auto_save_video")
        val VIDEO_RETENTION = stringPreferencesKey("video_retention")
    }

    override val autoSaveVideoEnabled: Flow<Boolean> = context.videoPreferencesDataStore.data.map { prefs ->
        prefs[Keys.AUTO_SAVE_VIDEO] ?: true
    }

    override val videoRetentionOption: Flow<VideoRetentionOption> = context.videoPreferencesDataStore.data.map { prefs ->
        prefs[Keys.VIDEO_RETENTION]?.let { runCatching { VideoRetentionOption.valueOf(it) }.getOrNull() } ?: VideoRetentionOption.COUNT_50
    }

    override suspend fun setAutoSaveVideoEnabled(enabled: Boolean) {
        context.videoPreferencesDataStore.edit { prefs ->
            prefs[Keys.AUTO_SAVE_VIDEO] = enabled
        }
    }

    override suspend fun setVideoRetentionOption(option: VideoRetentionOption) {
        context.videoPreferencesDataStore.edit { prefs ->
            prefs[Keys.VIDEO_RETENTION] = option.name
        }
    }
}
