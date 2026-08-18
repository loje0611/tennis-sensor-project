package io.github.loje0611.tennisdoc.core.data.repository

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface VideoFileManager {
    fun getVideoDirectory(): File
    fun generateVideoFile(sessionId: String, recordId: Long): File
    fun getUsedStorageBytes(): Long
    fun formatStorageSize(bytes: Long): String
    suspend fun deleteVideoFile(filePath: String): Boolean
    suspend fun clearAllVideos(): Int
    suspend fun enforceRetentionPolicy(maxCount: Int): Int
}

class VideoFileManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val labRawRecordDao: LabRawRecordDao
) : VideoFileManager {

    override fun getVideoDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "swing_videos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    override fun generateVideoFile(sessionId: String, recordId: Long): File {
        return File(getVideoDirectory(), "swing_${sessionId}_${recordId}.mp4")
    }

    override fun getUsedStorageBytes(): Long {
        val dir = getVideoDirectory()
        var size = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    override fun formatStorageSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.getDefault(), "%.1f MB", mb)
    }

    override suspend fun deleteVideoFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val deleted = if (file.exists()) file.delete() else false
        labRawRecordDao.clearVideoPathByPath(filePath)
        deleted
    }

    override suspend fun clearAllVideos(): Int = withContext(Dispatchers.IO) {
        val dir = getVideoDirectory()
        var deletedCount = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.delete()) {
                deletedCount++
            }
        }
        labRawRecordDao.clearAllVideoPaths()
        deletedCount
    }

    override suspend fun enforceRetentionPolicy(maxCount: Int): Int = withContext(Dispatchers.IO) {
        if (maxCount <= 0) return@withContext 0

        val records = labRawRecordDao.getRecordsWithVideoAsc()
        if (records.size <= maxCount) return@withContext 0

        val exceedCount = records.size - maxCount
        var deletedCount = 0

        for (i in 0 until exceedCount) {
            val record = records[i]
            val videoPath = record.videoPath
            if (videoPath != null) {
                val file = File(videoPath)
                if (!file.exists() || file.delete()) {
                    labRawRecordDao.updateVideoPath(record.id, null)
                    deletedCount++
                }
            }
        }
        deletedCount
    }
}
