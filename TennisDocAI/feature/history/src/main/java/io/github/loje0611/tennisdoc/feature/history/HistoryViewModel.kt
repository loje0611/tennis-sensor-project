package io.github.loje0611.tennisdoc.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: SwingHistoryRepository,
) : ViewModel() {

    val sessions: StateFlow<List<SwingSessionEntity>> =
        repository.observeSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalSwingsAllSessions: StateFlow<Int> =
        sessions
            .map { list -> list.sumOf { it.totalSwingCount } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val totalVolleysAllSessions: StateFlow<Int> =
        sessions
            .map { list ->
                list.sumOf { it.forehandVolleyCount + it.backhandVolleyCount }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _mockInsertInProgress = MutableStateFlow(false)
    val mockInsertInProgress: StateFlow<Boolean> = _mockInsertInProgress.asStateFlow()

    fun insertMockSessionData() {
        if (_mockInsertInProgress.value) return
        _mockInsertInProgress.value = true

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    MockDataGenerator.generateAndInsert(repository)
                }
            } finally {
                _mockInsertInProgress.value = false
            }
        }
    }
}
