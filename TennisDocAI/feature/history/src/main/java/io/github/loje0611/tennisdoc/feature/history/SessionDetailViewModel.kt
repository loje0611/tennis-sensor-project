package io.github.loje0611.tennisdoc.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SessionSwingCountEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngine
import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngineImpl
import io.github.loje0611.tennisdoc.core.fusion.engine.LabRawRecordParser
import io.github.loje0611.tennisdoc.core.model.CoachingCommentGenerator
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService
import io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 구종 하나에 대한 분석 결과 캐시 단위. */
data class CategoryAnalysisData(
    val metrics: SwingMetrics? = null,
    val historyMetrics: SwingMetrics? = null,
    val coachingComment: String = "",
    val loading: Boolean = true,
)

enum class SessionDetailTab {
    ANALYSIS,
    REPLAY,
    AI_COACH
}

data class SessionDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val session: SwingSessionEntity? = null,
    val breakdown: List<SessionSwingCountEntity> = emptyList(),
    val analysisCache: Map<String, CategoryAnalysisData> = emptyMap(),
    val labDetailState: LabSessionDetailUiState = LabSessionDetailUiState(),
    val selectedTab: SessionDetailTab = SessionDetailTab.ANALYSIS,
    val aiCoachReport: AiCoachReport? = null,
    val isGeneratingAiReport: Boolean = false,
    val selectedTone: CoachTone = CoachTone.ENCOURAGING
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SwingHistoryRepository,
    private val coachingCommentGenerator: CoachingCommentGenerator,
    private val reportParser: StructuredReportParser,
    private val compositeAiCoachService: CompositeAiCoachService,
) : ViewModel() {

    private var fusionEngine: FusionEngine = FusionEngineImpl()

    constructor(
        savedStateHandle: SavedStateHandle,
        repository: SwingHistoryRepository,
        coachingCommentGenerator: CoachingCommentGenerator,
        reportParser: StructuredReportParser,
        compositeAiCoachService: CompositeAiCoachService,
        fusionEngine: FusionEngine,
    ) : this(savedStateHandle, repository, coachingCommentGenerator, reportParser, compositeAiCoachService) {
        this.fusionEngine = fusionEngine
    }

    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        if (sessionId.isBlank()) {
            _uiState.value = SessionDetailUiState(loading = false, notFound = true)
        } else {
            viewModelScope.launch {
                val detail = withContext(Dispatchers.IO) {
                    repository.getSessionDetail(sessionId)
                }
                if (detail == null) {
                    _uiState.update {
                        it.copy(loading = false, notFound = true, session = null, breakdown = emptyList())
                    }
                } else {
                    val report = detail.session.aiCoachReportJson?.takeIf { it.isNotBlank() }?.let { json ->
                        reportParser.parseReport(json, detail.session.sessionId).getOrNull()
                    }

                    _uiState.update {
                        it.copy(
                            loading = false,
                            notFound = false,
                            session = detail.session,
                            breakdown = detail.breakdown,
                            labDetailState = it.labDetailState.copy(
                                session = detail.session,
                                isLoading = detail.session.sessionType == "LAB"
                            ),
                            aiCoachReport = report
                        )
                    }

                    if (detail.session.sessionType == "LAB") {
                        observeLabRawRecords(detail.session)
                    }
                }
            }
        }
    }

    private fun observeLabRawRecords(session: SwingSessionEntity) {
        viewModelScope.launch {
            repository.getLabRawRecordsForSession(session.sessionId).collectLatest { records ->
                val (items, squareRate, avgEff) = withContext(Dispatchers.Default) {
                    processLabRecords(records, session)
                }
                _uiState.update { current ->
                    current.copy(
                        labDetailState = current.labDetailState.copy(
                            session = session,
                            swingItems = items,
                            squareRatePercent = squareRate,
                            averageEnergyEfficiency = avgEff,
                            isLoading = false
                        )
                    )
                }
            }
        }
    }

    private fun processLabRecords(
        records: List<LabRawRecordEntity>,
        session: SwingSessionEntity
    ): Triple<List<LabSwingSummaryItem>, Int, Float> {
        val items = records.mapIndexed { idx, record ->
            val drill = runCatching { DrillType.valueOf(record.drillType) }
                .getOrElse {
                    session.drillType?.let { runCatching { DrillType.valueOf(it) }.getOrNull() }
                        ?: DrillType.FOREHAND
                }
            val fused = LabRawRecordParser.parseFusedSwing(
                drillType = drill,
                imuJson = record.imuRawJson,
                posesJson = record.visionPosesJson,
                fusionEngine = fusionEngine
            )
            val faceState = fused.racketImpact.faceState.name
            val energyEff = fused.kineticChain.energyTransferEfficiency
            val feedback = fused.diagnosis?.coachingFeedback
                ?: (fused.diagnosis?.primaryCause ?: "스윙 분석 완료")

            LabSwingSummaryItem(
                recordId = record.id,
                swingIndex = idx + 1,
                timestampMillis = record.timestampMillis,
                faceState = faceState,
                energyEfficiency = energyEff,
                coachingFeedback = feedback,
                fusedSwing = fused
            )
        }

        val squareCount = items.count { it.faceState == "SQUARE" }
        val squareRatePercent = if (items.isNotEmpty()) {
            (squareCount * 100 / items.size)
        } else {
            0
        }
        val avgEfficiency = if (items.isNotEmpty()) {
            items.map { it.energyEfficiency }.average().toFloat()
        } else {
            0f
        }

        return Triple(items, squareRatePercent, avgEfficiency)
    }

    fun preloadAllCategories(keys: List<String>) {
        keys.forEach { ensureCategoryLoaded(it) }
    }

    fun ensureCategoryLoaded(categoryKey: String) {
        val cached = _uiState.value.analysisCache[categoryKey]
        if (cached != null) return

        _uiState.update {
            it.copy(analysisCache = it.analysisCache + (categoryKey to CategoryAnalysisData()))
        }
        viewModelScope.launch { loadCategoryAnalysis(categoryKey) }
    }

    private suspend fun loadCategoryAnalysis(categoryKey: String) {
        val (avg, globalAvg) = withContext(Dispatchers.IO) {
            val a = repository.getAverageMetrics(sessionId, categoryKey)
            val g = repository.getGlobalAverageMetrics(categoryKey)
            a to g
        }

        val result = if (avg == null) {
            CategoryAnalysisData(
                coachingComment = "아직 분석 데이터가 없습니다.",
                loading = false,
            )
        } else {
            val metrics = SwingMetrics(
                power = avg.power.toInt().coerceIn(0, 100),
                spin = avg.spin.toInt().coerceIn(0, 100),
                timing = avg.timing.toInt().coerceIn(0, 100),
                smoothness = avg.fluidity.toInt().coerceIn(0, 100),
                stability = avg.stability.toInt().coerceIn(0, 100),
                consistency = avg.consistency.toInt().coerceIn(0, 100),
            )
            CategoryAnalysisData(
                metrics = metrics,
                historyMetrics = globalAvg,
                coachingComment = coachingCommentGenerator.generateComment(categoryKey, metrics, globalAvg),
                loading = false,
            )
        }

        _uiState.update {
            it.copy(analysisCache = it.analysisCache + (categoryKey to result))
        }
    }

    fun deleteSession(onComplete: () -> Unit) {
        if (sessionId.isBlank()) return
        viewModelScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) { repository.deleteSession(sessionId) }
            onComplete()
        }
    }

    fun selectTab(tab: SessionDetailTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectTone(tone: CoachTone) {
        _uiState.update { it.copy(selectedTone = tone) }
    }

    fun requestAiCoachReport(tone: CoachTone = CoachTone.ENCOURAGING) {
        val currentSession = _uiState.value.session ?: return
        
        _uiState.update { it.copy(isGeneratingAiReport = true) }
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.Default) {
                    val fusedSwings = _uiState.value.labDetailState.swingItems.mapNotNull { it.fusedSwing }
                    val drillType = currentSession.drillType?.let { runCatching { DrillType.valueOf(it) }.getOrNull() } ?: DrillType.FOREHAND
                    val contextBuilder = io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContextBuilder()
                    val context = contextBuilder.buildContext(
                        sessionId = currentSession.sessionId,
                        swings = fusedSwings,
                        drillType = drillType
                    )
                    val result = compositeAiCoachService.createReport(context, tone = tone)
                    
                    if (result != null) {
                        val root = org.json.JSONObject()
                        root.put("reportId", result.reportId)
                        root.put("sessionId", result.sessionId)
                        root.put("overallSummary", result.overallSummary)
                        root.put("keyStrengths", org.json.JSONArray(result.keyStrengths))
                        root.put("actionItems", org.json.JSONArray(result.actionItems))
                        
                        result.primaryFlawDiagnosis?.let { flaw ->
                            val flawObj = org.json.JSONObject()
                            flawObj.put("flawTitle", flaw.flawTitle)
                            flawObj.put("observedEffect", flaw.observedEffect)
                            flawObj.put("rootCause", flaw.rootCause)
                            flawObj.put("coachingCue", flaw.coachingCue)
                            root.put("primaryFlawDiagnosis", flawObj)
                        }
                        
                        val drillsArray = org.json.JSONArray()
                        result.recommendedDrills.forEach { drill ->
                            val drillObj = org.json.JSONObject()
                            drillObj.put("drillType", drill.drillType.name)
                            drillObj.put("title", drill.title)
                            drillObj.put("focusPoint", drill.focusPoint)
                            drillObj.put("targetRepetitions", drill.targetRepetitions)
                            drillsArray.put(drillObj)
                        }
                        root.put("recommendedDrills", drillsArray)
                        
                        val jsonString = root.toString()
                        repository.saveAiCoachReport(currentSession.sessionId, jsonString, System.currentTimeMillis())
                    }
                    result
                }
                
                _uiState.update { 
                    it.copy(
                        isGeneratingAiReport = false,
                        aiCoachReport = report ?: it.aiCoachReport
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingAiReport = false) }
            }
        }
    }
}
