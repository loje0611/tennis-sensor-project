package io.github.loje0611.tennisdoc.settings

import io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.repository.VideoFileManagerImpl
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VideoFileManagerTest {

    private lateinit var dao: FakeLabRawRecordDao
    private lateinit var manager: VideoFileManagerImpl
    private lateinit var videoDir: File

    @Before
    fun setUp() {
        dao = FakeLabRawRecordDao()
        manager = VideoFileManagerImpl(RuntimeEnvironment.getApplication(), dao)
        videoDir = manager.getVideoDirectory()
        videoDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        videoDir.listFiles()?.forEach { it.delete() }
        videoDir.delete()
    }

    @Test
    fun generateVideoFileLivesUnderSwingVideosDirectory() {
        val file = manager.generateVideoFile("session-a", 42L)
        assertEquals("swing_session-a_42.mp4", file.name)
        assertEquals(videoDir.canonicalFile, file.parentFile?.canonicalFile)
        assertTrue(file.absolutePath.contains("swing_videos"))
    }

    @Test
    fun formatStorageSizeFormatsZeroAsZeroMb() {
        assertEquals("0 MB", manager.formatStorageSize(0L))
        assertEquals("0 MB", manager.formatStorageSize(-10L))
    }

    @Test
    fun getUsedStorageBytesSumsFilesInDirectory() {
        val a = manager.generateVideoFile("s", 1L)
        val b = manager.generateVideoFile("s", 2L)
        a.writeBytes(ByteArray(40))
        b.writeBytes(ByteArray(60))
        assertEquals(100L, manager.getUsedStorageBytes())
    }

    @Test
    fun deleteVideoFileReturnsFalseWhenMissingAndClearsDbPath() = runTest {
        val missing = File(videoDir, "missing.mp4").absolutePath
        dao.records.add(
            LabRawRecordEntity(
                id = 7L,
                sessionId = "s",
                drillType = "FOREHAND",
                timestampMillis = 1L,
                imuRawJson = "[imu]",
                visionPosesJson = "[pose]",
                videoPath = missing,
            ),
        )

        assertFalse(manager.deleteVideoFile(missing))
        assertNull(dao.records.single { it.id == 7L }.videoPath)
        assertEquals("[pose]", dao.records.single { it.id == 7L }.visionPosesJson)
        assertEquals("[imu]", dao.records.single { it.id == 7L }.imuRawJson)
    }

    @Test
    fun clearAllVideosDeletesFilesAndNullsAllPaths() = runTest {
        val files = (1L..3L).map { id ->
            val file = manager.generateVideoFile("s", id)
            file.writeText("clip-$id")
            dao.records.add(record(id, id, file.absolutePath))
            file
        }
        dao.records.add(record(99L, 99L, null))

        val deleted = manager.clearAllVideos()

        assertEquals(3, deleted)
        files.forEach { assertFalse(it.exists()) }
        dao.records.forEach { assertNull(it.videoPath) }
        assertEquals("[pose-99]", dao.records.single { it.id == 99L }.visionPosesJson)
    }

    @Test
    fun enforceRetentionPolicy50DeletesOldestOverflowAndKeepsPoseJson() = runTest {
        val files = (1L..51L).map { id ->
            val file = manager.generateVideoFile("s", id)
            file.writeText("clip-$id")
            dao.records.add(record(id, timestamp = id, videoPath = file.absolutePath))
            file
        }

        val deleted = manager.enforceRetentionPolicy(50)

        assertEquals(1, deleted)
        assertFalse(files[0].exists())
        assertTrue(files[1].exists())
        assertTrue(files[50].exists())
        assertNull(dao.records.single { it.id == 1L }.videoPath)
        assertEquals("[pose-1]", dao.records.single { it.id == 1L }.visionPosesJson)
        assertEquals("[imu-1]", dao.records.single { it.id == 1L }.imuRawJson)
        assertEquals(files[1].absolutePath, dao.records.single { it.id == 2L }.videoPath)
        assertEquals(50, dao.records.count { it.videoPath != null })
    }

    @Test
    fun enforceRetentionPolicySkipsUnlimitedAndDoesNotDelete() = runTest {
        val file = manager.generateVideoFile("s", 1L)
        file.writeText("keep")
        dao.records.add(record(1L, 1L, file.absolutePath))

        assertEquals(0, manager.enforceRetentionPolicy(-1))
        assertEquals(0, manager.enforceRetentionPolicy(0))
        assertTrue(file.exists())
        assertEquals(file.absolutePath, dao.records.single().videoPath)
    }

    @Test
    fun enforceRetentionPolicyClearsPathWhenFileAlreadyMissing() = runTest {
        val missing = File(videoDir, "gone.mp4").absolutePath
        dao.records.add(record(1L, 1L, missing))
        dao.records.add(record(2L, 2L, manager.generateVideoFile("s", 2L).also { it.writeText("keep") }.absolutePath))

        val deleted = manager.enforceRetentionPolicy(1)

        assertEquals(1, deleted)
        assertNull(dao.records.single { it.id == 1L }.videoPath)
        assertEquals("[pose-1]", dao.records.single { it.id == 1L }.visionPosesJson)
        assertTrue(File(dao.records.single { it.id == 2L }.videoPath!!).exists())
    }

    private fun record(id: Long, timestamp: Long, videoPath: String?) = LabRawRecordEntity(
        id = id,
        sessionId = "s",
        drillType = "FOREHAND",
        timestampMillis = timestamp,
        imuRawJson = "[imu-$id]",
        visionPosesJson = "[pose-$id]",
        videoPath = videoPath,
    )

    private class FakeLabRawRecordDao : LabRawRecordDao {
        val records = mutableListOf<LabRawRecordEntity>()

        override suspend fun insert(record: LabRawRecordEntity): Long {
            val id = if (record.id == 0L) (records.maxOfOrNull { it.id } ?: 0L) + 1L else record.id
            records.add(record.copy(id = id))
            return id
        }

        override fun getRecordsBySessionId(sessionId: String): Flow<List<LabRawRecordEntity>> =
            MutableStateFlow(records.filter { it.sessionId == sessionId })

        override suspend fun getRecordById(id: Long): LabRawRecordEntity? =
            records.firstOrNull { it.id == id }

        override suspend fun deleteRecordsBySessionId(sessionId: String): Int {
            val before = records.size
            records.removeAll { it.sessionId == sessionId }
            return before - records.size
        }

        override suspend fun updateVideoPath(id: Long, videoPath: String?) {
            val index = records.indexOfFirst { it.id == id }
            if (index >= 0) {
                records[index] = records[index].copy(videoPath = videoPath)
            }
        }

        override suspend fun getRecordsWithVideoAsc(): List<LabRawRecordEntity> =
            records.filter { it.videoPath != null }.sortedBy { it.timestampMillis }

        override fun observeVideoRecordCount(): Flow<Int> =
            MutableStateFlow(records).map { list -> list.count { it.videoPath != null } }

        override suspend fun clearVideoPathByPath(videoPath: String) {
            records.replaceAll { if (it.videoPath == videoPath) it.copy(videoPath = null) else it }
        }

        override suspend fun clearAllVideoPaths() {
            records.replaceAll { it.copy(videoPath = null) }
        }
    }
}
