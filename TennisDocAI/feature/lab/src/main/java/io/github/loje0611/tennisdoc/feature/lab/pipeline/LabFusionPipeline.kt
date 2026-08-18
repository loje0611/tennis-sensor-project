package io.github.loje0611.tennisdoc.feature.lab.pipeline

import android.graphics.Bitmap
import io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.repository.VideoFileManager
import io.github.loje0611.tennisdoc.core.data.repository.VideoPreferencesRepository
import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport
import io.github.loje0611.tennisdoc.core.fusion.anomaly.PersonalBaseline
import io.github.loje0611.tennisdoc.core.fusion.anomaly.StatisticalAnomalyDetector
import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngine
import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngineImpl
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

interface LabFusionPipeline {
    val latestFusedSwing: StateFlow<FusedSwing?>
    val latestAnomalyReport: StateFlow<BaselineComparisonReport?>
    val currentBaseline: StateFlow<PersonalBaseline?>
    val latestRecordedId: StateFlow<Long?>

    fun feedPoseFrame(frame: PoseFrame)
    fun feedImuSample(sample: ImuDataPoint)
    fun feedVideoFrame(bitmap: Bitmap, timestampMs: Long)
    suspend fun onSwingTriggered(sessionId: String, drillType: DrillType): FusedSwing?
    fun reset()
}

class LabFusionPipelineImpl(
    private val buffer: LabFusionStreamBuffer = LabFusionStreamBuffer(),
    private val fusionEngine: FusionEngine = FusionEngineImpl(),
    private val anomalyDetector: StatisticalAnomalyDetector = StatisticalAnomalyDetector(),
    private val labRawRecordDao: LabRawRecordDao? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val videoPreferencesRepository: VideoPreferencesRepository? = null,
    private val videoFileManager: VideoFileManager? = null,
    private val videoEncoder: SwingVideoEncoder = SwingVideoEncoderImpl(),
    private val videoBuffer: SwingVideoBuffer = SwingVideoBuffer()
) : LabFusionPipeline {

    private val _latestFusedSwing = MutableStateFlow<FusedSwing?>(null)
    override val latestFusedSwing: StateFlow<FusedSwing?> = _latestFusedSwing.asStateFlow()

    private val _latestAnomalyReport = MutableStateFlow<BaselineComparisonReport?>(null)
    override val latestAnomalyReport: StateFlow<BaselineComparisonReport?> = _latestAnomalyReport.asStateFlow()

    private val _currentBaseline = MutableStateFlow<PersonalBaseline?>(null)
    override val currentBaseline: StateFlow<PersonalBaseline?> = _currentBaseline.asStateFlow()

    private val _latestRecordedId = MutableStateFlow<Long?>(null)
    override val latestRecordedId: StateFlow<Long?> = _latestRecordedId.asStateFlow()

    override fun feedPoseFrame(frame: PoseFrame) {
        buffer.addPoseFrame(frame)
    }

    override fun feedImuSample(sample: ImuDataPoint) {
        buffer.addImuSample(sample)
    }

    override fun feedVideoFrame(bitmap: Bitmap, timestampMs: Long) {
        videoBuffer.addFrame(bitmap, timestampMs)
    }

    override suspend fun onSwingTriggered(sessionId: String, drillType: DrillType): FusedSwing? {
        val (poses, imuSamples) = buffer.snapshot()
        if (poses.isEmpty() && imuSamples.isEmpty()) {
            return null
        }

        val fused = fusionEngine.fuse(drillType, poses, imuSamples)
        val updatedBaseline = anomalyDetector.updateBaseline(_currentBaseline.value, fused)
        val report = anomalyDetector.detectAnomalies(updatedBaseline, fused)

        _currentBaseline.value = updatedBaseline
        _latestFusedSwing.value = fused
        _latestAnomalyReport.value = report

        var videoPath: String? = null
        if (videoPreferencesRepository != null && videoFileManager != null) {
            val autoSave = videoPreferencesRepository.autoSaveVideoEnabled.first()
            if (autoSave) {
                val videoFrames = videoBuffer.snapshot()
                if (videoFrames.isNotEmpty()) {
                    val tempRecordId = System.currentTimeMillis()
                    val targetFile = videoFileManager.generateVideoFile(sessionId, tempRecordId)
                    val success = videoEncoder.encodeToMp4(videoFrames, targetFile)
                    if (success) {
                        videoPath = targetFile.absolutePath
                    }
                }
                videoFrames.forEach { it.bitmap.recycle() }
            }
        }

        labRawRecordDao?.let { dao ->
            withContext(ioDispatcher + NonCancellable) {
                try {
                    val record = LabRawRecordEntity(
                        sessionId = sessionId,
                        drillType = drillType.name,
                        timestampMillis = System.currentTimeMillis(),
                        imuRawJson = serializeImu(imuSamples),
                        visionPosesJson = serializePoses(poses),
                        impactOffsetMs = fused.anchor.timeOffsetMs,
                        videoPath = videoPath
                    )
                    val insertedId = dao.insert(record)
                    _latestRecordedId.value = insertedId
                    
                    if (videoPath != null && videoPreferencesRepository != null && videoFileManager != null) {
                        val option = videoPreferencesRepository.videoRetentionOption.first()
                        videoFileManager.enforceRetentionPolicy(option.maxCount)
                    }
                } catch (_: Exception) {
                    // Fail-safe logging for Room DB insert
                }
            }
        }

        return fused
    }

    override fun reset() {
        buffer.clear()
        videoBuffer.clear()
        _latestFusedSwing.value = null
        _latestAnomalyReport.value = null
        _latestRecordedId.value = null
    }

    private fun serializeImu(samples: List<ImuDataPoint>): String {
        return samples.joinToString(separator = ",", prefix = "[", postfix = "]") {
            "{\"ts\":${it.timestampMs},\"ax\":${it.accelX},\"ay\":${it.accelY},\"az\":${it.accelZ},\"gx\":${it.gyroX},\"gy\":${it.gyroY},\"gz\":${it.gyroZ}}"
        }
    }

    private fun serializePoses(poses: List<PoseFrame>): String {
        return poses.joinToString(separator = ",", prefix = "[", postfix = "]") { frame ->
            val lms = frame.landmarks.joinToString(separator = ",", prefix = "[", postfix = "]") {
                "{\"x\":${it.x},\"y\":${it.y},\"z\":${it.z},\"v\":${it.visibility}}"
            }
            "{\"landmarks\":$lms}"
        }
    }
}
