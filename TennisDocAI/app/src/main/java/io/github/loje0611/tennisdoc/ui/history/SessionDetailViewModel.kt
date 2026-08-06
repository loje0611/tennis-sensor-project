package io.github.loje0611.tennisdoc.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.loje0611.tennisdoc.analysis.CoachingEngine
import io.github.loje0611.tennisdoc.analysis.SwingMetrics
import io.github.loje0611.tennisdoc.data.db.entity.SessionSwingCountEntity
import io.github.loje0611.tennisdoc.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.data.repository.SwingHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class SessionDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val session: SwingSessionEntity? = null,
    val breakdown: List<SessionSwingCountEntity> = emptyList(),
    val analysisCache: Map<String, CategoryAnalysisData> = emptyMap(),
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SwingHistoryRepository,
) : ViewModel() {

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
                _uiState.update {
                    if (detail == null) {
                        it.copy(loading = false, notFound = true, session = null, breakdown = emptyList())
                    } else {
                        it.copy(
                            loading = false,
                            notFound = false,
                            session = detail.session,
                            breakdown = detail.breakdown,
                        )
                    }
                }
            }
        }
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
                coachingComment = CoachingEngine.generateComment(categoryKey, metrics, globalAvg),
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
}
