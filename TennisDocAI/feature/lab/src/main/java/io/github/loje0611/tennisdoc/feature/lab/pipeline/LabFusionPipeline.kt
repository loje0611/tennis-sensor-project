package io.github.loje0611.tennisdoc.feature.lab.pipeline

import io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
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
import kotlinx.coroutines.withContext

interface LabFusionPipeline {
    val latestFusedSwing: StateFlow<FusedSwing?>
    val latestAnomalyReport: StateFlow<BaselineComparisonReport?>
    val currentBaseline: StateFlow<PersonalBaseline?>

    fun feedPoseFrame(frame: PoseFrame)
    fun feedImuSample(sample: ImuDataPoint)
    suspend fun onSwingTriggered(sessionId: String, drillType: DrillType): FusedSwing?
    fun reset()
}

class LabFusionPipelineImpl(
    private val buffer: LabFusionStreamBuffer = LabFusionStreamBuffer(),
    private val fusionEngine: FusionEngine = FusionEngineImpl(),
    private val anomalyDetector: StatisticalAnomalyDetector = StatisticalAnomalyDetector(),
    private val labRawRecordDao: LabRawRecordDao? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LabFusionPipeline {

    private val _latestFusedSwing = MutableStateFlow<FusedSwing?>(null)
    override val latestFusedSwing: StateFlow<FusedSwing?> = _latestFusedSwing.asStateFlow()

    private val _latestAnomalyReport = MutableStateFlow<BaselineComparisonReport?>(null)
    override val latestAnomalyReport: StateFlow<BaselineComparisonReport?> = _latestAnomalyReport.asStateFlow()

    private val _currentBaseline = MutableStateFlow<PersonalBaseline?>(null)
    override val currentBaseline: StateFlow<PersonalBaseline?> = _currentBaseline.asStateFlow()

    override fun feedPoseFrame(frame: PoseFrame) {
        buffer.addPoseFrame(frame)
    }

    override fun feedImuSample(sample: ImuDataPoint) {
        buffer.addImuSample(sample)
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

        labRawRecordDao?.let { dao ->
            withContext(ioDispatcher + NonCancellable) {
                try {
                    val record = LabRawRecordEntity(
                        sessionId = sessionId,
                        drillType = drillType.name,
                        timestampMillis = System.currentTimeMillis(),
                        imuRawJson = serializeImu(imuSamples),
                        visionPosesJson = serializePoses(poses),
                        impactOffsetMs = fused.anchor.timeOffsetMs
                    )
                    dao.insert(record)
                } catch (_: Exception) {
                    // Fail-safe logging for Room DB insert
                }
            }
        }

        return fused
    }

    override fun reset() {
        buffer.clear()
        _latestFusedSwing.value = null
        _latestAnomalyReport.value = null
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
