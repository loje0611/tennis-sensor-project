package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngine
import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngineImpl
import io.github.loje0611.tennisdoc.core.fusion.engine.LabRawRecordParser
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class LabReplayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SwingHistoryRepository,
) : ViewModel() {

    private var fusionEngine: FusionEngine = FusionEngineImpl()

    constructor(
        savedStateHandle: SavedStateHandle?,
        repository: SwingHistoryRepository?,
        fusionEngine: FusionEngine = FusionEngineImpl(),
    ) : this(
        savedStateHandle ?: SavedStateHandle(),
        repository ?: object : SwingHistoryRepository {
            override fun observeSessions() = kotlinx.coroutines.flow.emptyFlow<List<io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity>>()
            override suspend fun generateCsvString(sessionId: String?, startTimeMillis: Long?, endTimeMillis: Long?) = ""
            override suspend fun getSessionDetail(sessionId: String) = null
            override suspend fun deleteSession(sessionId: String) {}
            override suspend fun insertProvisionalSession(session: io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity) {}
            override suspend fun finalizeSession(sessionId: String, endTime: Long, totalSwingCount: Int, durationMillis: Long, fhVolley: Int, bhVolley: Int, breakdownNormalized: Map<String, Int>) {}
            override suspend fun insertSessionWithBreakdown(session: io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity, breakdown: List<Pair<String, Int>>) {}
            override suspend fun insertMockSession(session: io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity, breakdownMap: Map<String, Int>, events: List<io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity>) {}
            override suspend fun insertSwingEvent(event: io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity) {}
            override suspend fun getAverageMetrics(sessionId: String, categoryKey: String) = null
            override suspend fun getSwingEventsForSession(sessionId: String) = emptyList<io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity>()
            override suspend fun updateGlobalStatistics(categoryKey: String, metrics: io.github.loje0611.tennisdoc.core.model.SwingMetrics) {}
            override suspend fun batchUpdateGlobalStatistics(events: List<io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity>) {}
            override suspend fun getGlobalAverageMetrics(categoryKey: String) = null
            override fun getLabRawRecordsForSession(sessionId: String) = kotlinx.coroutines.flow.emptyFlow<List<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>>()
            override suspend fun getLabRawRecordById(recordId: Long) = null
            override suspend fun insertLabRawRecord(record: io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity) = 0L
        }
    ) {
        this.fusionEngine = fusionEngine
    }

    constructor() : this(null, null, FusionEngineImpl())

    private val _uiState = MutableStateFlow(LabReplayUiState())
    val uiState: StateFlow<LabReplayUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    init {
        val recordId = savedStateHandle.get<Long>("recordId")
            ?: savedStateHandle.get<String>("recordId")?.toLongOrNull()
        if (recordId != null && recordId > 0L) {
            loadRecord(recordId)
        }
    }

    fun loadRecord(recordId: Long) {
        val repo = repository ?: return
        viewModelScope.launch {
            val record = withContext(Dispatchers.IO) {
                repo.getLabRawRecordById(recordId)
            }
            if (record != null) {
                val drill = runCatching { DrillType.valueOf(record.drillType) }
                    .getOrDefault(DrillType.FOREHAND)
                val fused = withContext(Dispatchers.Default) {
                    LabRawRecordParser.parseFusedSwing(
                        drillType = drill,
                        imuJson = record.imuRawJson,
                        posesJson = record.visionPosesJson,
                        fusionEngine = fusionEngine
                    )
                }
                setFusedSwing(fused)
            }
        }
    }

    fun setFusedSwing(fusedSwing: FusedSwing?) {
        playbackJob?.cancel()
        if (fusedSwing == null) {
            _uiState.value = LabReplayUiState()
            return
        }

        val visionCount = fusedSwing.visionPoses.size
        val imuCount = fusedSwing.imuSamples.size

        val durationFromVision = if (visionCount > 0) (visionCount - 1) * 33L else 0L
        val durationFromImu = if (imuCount > 0) {
            val first = fusedSwing.imuSamples.first().timestampMs
            val last = fusedSwing.imuSamples.last().timestampMs
            (last - first).coerceAtLeast(0L)
        } else 0L

        val durationMs = maxOf(durationFromVision, durationFromImu).coerceAtLeast(100L)

        val impactTimestampMs = if (fusedSwing.anchor.isSynchronized) {
            if (fusedSwing.anchor.visionImpactTimestampMs > 0L && durationFromVision > 0L) {
                fusedSwing.anchor.visionImpactTimestampMs.coerceIn(0L, durationMs)
            } else {
                durationMs / 2
            }
        } else {
            durationMs / 2
        }

        _uiState.value = LabReplayUiState(
            fusedSwing = fusedSwing,
            durationMs = durationMs,
            currentTimestampMs = 0L,
            isPlaying = false,
            playbackSpeed = 1.0f,
            impactTimestampMs = impactTimestampMs
        )

        seekTo(0L)
    }

    fun seekTo(timestampMs: Long) {
        val current = _uiState.value
        val swing = current.fusedSwing ?: return

        val clampedTs = timestampMs.coerceIn(0L, current.durationMs)
        val timeOffset = if (swing.anchor.isSynchronized) swing.anchor.timeOffsetMs else 0L

        // 비전 포즈 탐색
        val poseFrame = findNearestPose(swing.visionPoses, clampedTs, current.durationMs)

        // IMU 샘플 탐색 (timeOffset 보정)
        val imuPoint = findNearestImu(swing.imuSamples, clampedTs + timeOffset)

        // 임팩트 시점 판정 (|t - impactTs| <= 33ms)
        val isImpact = abs(clampedTs - current.impactTimestampMs) <= 33L

        // 툴팁 생성 (임팩트 시점에 진단 태그 기반)
        val tooltips = if (isImpact && poseFrame != null) {
            createTooltipsForDiagnosis(swing, poseFrame)
        } else {
            emptyList()
        }

        _uiState.update {
            it.copy(
                currentTimestampMs = clampedTs,
                currentPoseFrame = poseFrame,
                currentImuPoint = imuPoint,
                isImpactFrame = isImpact,
                tooltips = tooltips
            )
        }
    }

    fun jumpToImpact() {
        seekTo(_uiState.value.impactTimestampMs)
    }

    fun togglePlay() {
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        if (_uiState.value.fusedSwing == null) return
        playbackJob?.cancel()

        if (_uiState.value.currentTimestampMs >= _uiState.value.durationMs) {
            seekTo(0L)
        }

        _uiState.update { it.copy(isPlaying = true) }

        playbackJob = viewModelScope.launch {
            while (_uiState.value.isPlaying) {
                val delayTime = (33L / _uiState.value.playbackSpeed).toLong().coerceAtLeast(10L)
                delay(delayTime)

                val nextTs = _uiState.value.currentTimestampMs + 33L
                if (nextTs > _uiState.value.durationMs) {
                    seekTo(_uiState.value.durationMs)
                    pause()
                    break
                } else {
                    seekTo(nextTs)
                }
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun stepForward() {
        pause()
        seekTo(_uiState.value.currentTimestampMs + 33L)
    }

    fun stepBackward() {
        pause()
        seekTo(_uiState.value.currentTimestampMs - 33L)
    }

    private fun findNearestPose(poses: List<PoseFrame>, currentTs: Long, durationMs: Long): PoseFrame? {
        if (poses.isEmpty()) return null
        if (poses.size == 1 || durationMs <= 0L) return poses.first()

        val index = ((currentTs.toFloat() / durationMs.toFloat()) * (poses.size - 1))
            .toInt()
            .coerceIn(0, poses.lastIndex)
        return poses[index]
    }

    private fun findNearestImu(imuSamples: List<ImuDataPoint>, targetAbsoluteTs: Long): ImuDataPoint? {
        if (imuSamples.isEmpty()) return null
        if (imuSamples.size == 1) return imuSamples.first()

        var low = 0
        var high = imuSamples.lastIndex

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midTs = imuSamples[mid].timestampMs

            when {
                midTs < targetAbsoluteTs -> low = mid + 1
                midTs > targetAbsoluteTs -> high = mid - 1
                else -> return imuSamples[mid]
            }
        }

        val leftIdx = high.coerceIn(0, imuSamples.lastIndex)
        val rightIdx = low.coerceIn(0, imuSamples.lastIndex)

        val diffLeft = abs(imuSamples[leftIdx].timestampMs - targetAbsoluteTs)
        val diffRight = abs(imuSamples[rightIdx].timestampMs - targetAbsoluteTs)

        return if (diffLeft <= diffRight) imuSamples[leftIdx] else imuSamples[rightIdx]
    }

    private fun createTooltipsForDiagnosis(swing: FusedSwing, pose: PoseFrame): List<ReplayTooltip> {
        val diag = swing.diagnosis ?: return emptyList()
        val tags = diag.diagnosisTags
        val landmarks = pose.landmarks
        val tooltips = mutableListOf<ReplayTooltip>()

        // 1. 골반 / 상체 조기 개방 (Joint 24: 오른쪽 골반)
        if (tags.contains("EARLY_BODY_OPEN") || tags.contains("KINETIC_FAULT")) {
            val joint = landmarks.getOrNull(24) ?: landmarks.getOrNull(23)
            if (joint != null) {
                tooltips.add(
                    ReplayTooltip(
                        targetJointIndex = 24,
                        jointX = joint.x,
                        jointY = joint.y,
                        text = "골반 조기 회전 (타점 밀림)"
                    )
                )
            }
        }

        // 2. 라켓 페이스 상태 및 손목 각도 (Joint 16: 오른쪽 손목)
        if (tags.contains("FACE_OPEN") || tags.contains("FACE_CLOSED") || tags.contains("LATE_CONTACT")) {
            val joint = landmarks.getOrNull(16) ?: landmarks.getOrNull(15)
            if (joint != null) {
                val label = when {
                    tags.contains("FACE_OPEN") -> "페이스 열림 (${swing.racketImpact.deviationDeg.toInt()}°)"
                    tags.contains("FACE_CLOSED") -> "페이스 닫힘 (${swing.racketImpact.deviationDeg.toInt()}°)"
                    else -> "타점 지연 (Late Contact)"
                }
                tooltips.add(
                    ReplayTooltip(
                        targetJointIndex = 16,
                        jointX = joint.x,
                        jointY = joint.y,
                        text = label
                    )
                )
            }
        }

        // 3. 에너지 전달 지연 (Joint 12: 오른쪽 어깨)
        if (tags.contains("POWER_LEAK") || tags.contains("CHAIN_TIMING_DELAY")) {
            val joint = landmarks.getOrNull(12) ?: landmarks.getOrNull(11)
            if (joint != null) {
                tooltips.add(
                    ReplayTooltip(
                        targetJointIndex = 12,
                        jointX = joint.x,
                        jointY = joint.y,
                        text = "어깨 가속 지연"
                    )
                )
            }
        }

        // 4. 클린 스트라이크 (기본 손목 툴팁)
        if (tags.contains("CLEAN_STRIKE") || tags.contains("SQUARE_FACE")) {
            val joint = landmarks.getOrNull(16) ?: landmarks.getOrNull(15)
            if (joint != null) {
                tooltips.add(
                    ReplayTooltip(
                        targetJointIndex = 16,
                        jointX = joint.x,
                        jointY = joint.y,
                        text = "스퀘어 임팩트 (정상)"
                    )
                )
            }
        }

        return tooltips
    }
}
