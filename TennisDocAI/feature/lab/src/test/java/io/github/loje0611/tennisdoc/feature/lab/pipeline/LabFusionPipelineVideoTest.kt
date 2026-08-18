package io.github.loje0611.tennisdoc.feature.lab.pipeline

import android.graphics.Bitmap
import io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.repository.VideoFileManager
import io.github.loje0611.tennisdoc.core.data.repository.VideoPreferencesRepository
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.VideoRetentionOption
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LabFusionPipelineVideoTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeLabRawRecordDao : LabRawRecordDao {
        val insertedRecords = mutableListOf<LabRawRecordEntity>()

        override suspend fun insert(record: LabRawRecordEntity): Long {
            insertedRecords.add(record)
            return insertedRecords.size.toLong()
        }

        override fun getRecordsBySessionId(sessionId: String): Flow<List<LabRawRecordEntity>> =
            flowOf(insertedRecords.filter { it.sessionId == sessionId })

        override suspend fun getRecordById(id: Long): LabRawRecordEntity? =
            insertedRecords.firstOrNull { it.id == id }

        override suspend fun deleteRecordsBySessionId(sessionId: String): Int {
            val before = insertedRecords.size
            insertedRecords.removeAll { it.sessionId == sessionId }
            return before - insertedRecords.size
        }

        override suspend fun updateVideoPath(id: Long, videoPath: String?) {}
        override suspend fun getRecordsWithVideoAsc(): List<LabRawRecordEntity> = emptyList()
        override fun observeVideoRecordCount(): Flow<Int> = flowOf(0)
        override suspend fun clearVideoPathByPath(videoPath: String) {}
        override suspend fun clearAllVideoPaths() {}
    }

    private class FakeVideoPrefs(autoSave: Boolean) : VideoPreferencesRepository {
        override val autoSaveVideoEnabled = MutableStateFlow(autoSave)
        override val videoRetentionOption = MutableStateFlow(VideoRetentionOption.COUNT_50)
        override suspend fun setAutoSaveVideoEnabled(enabled: Boolean) {
            autoSaveVideoEnabled.value = enabled
        }
        override suspend fun setVideoRetentionOption(option: VideoRetentionOption) {
            videoRetentionOption.value = option
        }
    }

    private class FakeVideoFiles(private val dir: File) : VideoFileManager {
        var lastGenerated: File? = null
        var enforceCalls = 0
        var lastMaxCount: Int? = null

        override fun getVideoDirectory(): File = dir
        override fun generateVideoFile(sessionId: String, recordId: Long): File {
            lastGenerated = File(dir, "swing_${sessionId}_$recordId.mp4")
            return lastGenerated!!
        }
        override fun getUsedStorageBytes(): Long = 0L
        override fun formatStorageSize(bytes: Long) = "0 MB"
        override suspend fun deleteVideoFile(filePath: String) = true
        override suspend fun clearAllVideos() = 0
        override suspend fun enforceRetentionPolicy(maxCount: Int): Int {
            enforceCalls += 1
            lastMaxCount = maxCount
            return 0
        }
    }

    private class FakeEncoder(private val succeed: Boolean = true) : SwingVideoEncoder {
        var calls = 0
        override suspend fun encodeToMp4(
            frames: List<SwingVideoFrame>,
            outputFile: File,
            width: Int,
            height: Int,
            fps: Int,
            bitrate: Int,
        ): Boolean {
            calls += 1
            if (!succeed) return false
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(byteArrayOf(0, 0, 0, 20) + "ftypisom".toByteArray())
            return true
        }
    }

    private fun generateMockPose(yVal: Float): PoseFrame {
        val landmarks = (0..32).map { i ->
            if (i == 16) PoseLandmark(0.5f, yVal, 0.2f, 1f)
            else PoseLandmark(0.5f, 0.5f, 0f, 1f)
        }
        return PoseFrame(landmarks = landmarks)
    }

    private fun generateMockImu(timestampMs: Long, gyroZ: Float, accelX: Float) = ImuDataPoint(
        timestampMs = timestampMs,
        accelX = accelX,
        accelY = 0f,
        accelZ = 0f,
        gyroX = 0f,
        gyroY = 0f,
        gyroZ = gyroZ,
    )

    private fun LabFusionPipelineImpl.feedSwingInputs() {
        for (i in 0..20) {
            feedPoseFrame(generateMockPose(0.8f - i * 0.02f))
        }
        for (i in 0..50) {
            val ts = i * 20L
            val gyro = if (i == 25) 1500f else 100f
            val accel = if (i == 25) 15f else 1f
            feedImuSample(generateMockImu(ts, gyro, accel))
        }
    }

    @Test
    fun autoSaveOnStoresEncodedPathAndEnforcesRetention() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeLabRawRecordDao()
        val files = FakeVideoFiles(tmp.root)
        val encoder = FakeEncoder()
        val pipeline = LabFusionPipelineImpl(
            labRawRecordDao = dao,
            ioDispatcher = dispatcher,
            videoPreferencesRepository = FakeVideoPrefs(autoSave = true),
            videoFileManager = files,
            videoEncoder = encoder,
            videoBuffer = SwingVideoBuffer(),
        )
        pipeline.feedSwingInputs()
        pipeline.feedVideoFrame(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), 1000L)

        val swing = pipeline.onSwingTriggered("session-on", DrillType.FOREHAND)
        testScheduler.advanceUntilIdle()

        assertNotNull(swing)
        assertEquals(1, encoder.calls)
        assertEquals(1, dao.insertedRecords.size)
        val record = dao.insertedRecords.single()
        assertEquals(files.lastGenerated!!.absolutePath, record.videoPath)
        assertTrue(File(record.videoPath!!).exists())
        assertEquals(1, files.enforceCalls)
        assertEquals(VideoRetentionOption.COUNT_50.maxCount, files.lastMaxCount)
    }

    @Test
    fun autoSaveOffSkipsEncodeAndPersistsNullVideoPath() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeLabRawRecordDao()
        val files = FakeVideoFiles(tmp.root)
        val encoder = FakeEncoder()
        val pipeline = LabFusionPipelineImpl(
            labRawRecordDao = dao,
            ioDispatcher = dispatcher,
            videoPreferencesRepository = FakeVideoPrefs(autoSave = false),
            videoFileManager = files,
            videoEncoder = encoder,
            videoBuffer = SwingVideoBuffer(),
        )
        pipeline.feedSwingInputs()
        pipeline.feedVideoFrame(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), 1000L)

        val swing = pipeline.onSwingTriggered("session-off", DrillType.BACKHAND)
        testScheduler.advanceUntilIdle()

        assertNotNull(swing)
        assertEquals(0, encoder.calls)
        assertEquals(1, dao.insertedRecords.size)
        assertNull(dao.insertedRecords.single().videoPath)
        assertEquals(0, files.enforceCalls)
    }

    @Test
    fun encodeFailurePersistsNullPathAndSkipsRetention() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeLabRawRecordDao()
        val files = FakeVideoFiles(tmp.root)
        val encoder = FakeEncoder(succeed = false)
        val pipeline = LabFusionPipelineImpl(
            labRawRecordDao = dao,
            ioDispatcher = dispatcher,
            videoPreferencesRepository = FakeVideoPrefs(autoSave = true),
            videoFileManager = files,
            videoEncoder = encoder,
            videoBuffer = SwingVideoBuffer(),
        )
        pipeline.feedSwingInputs()
        pipeline.feedVideoFrame(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), 1000L)

        pipeline.onSwingTriggered("session-fail", DrillType.FOREHAND)
        testScheduler.advanceUntilIdle()

        assertEquals(1, encoder.calls)
        assertNull(dao.insertedRecords.single().videoPath)
        assertEquals(0, files.enforceCalls)
    }

    @Test
    fun resetClearsVideoBuffer() {
        val videoBuffer = SwingVideoBuffer()
        val pipeline = LabFusionPipelineImpl(videoBuffer = videoBuffer)
        pipeline.feedVideoFrame(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), 10L)
        assertFalse(videoBuffer.snapshot().isEmpty())
        pipeline.reset()
        assertTrue(videoBuffer.snapshot().isEmpty())
    }
}
