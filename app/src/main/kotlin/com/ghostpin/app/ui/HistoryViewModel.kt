package com.ghostpin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.data.db.SimulationHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val simulationRepository: SimulationRepository,
    ) : ViewModel() {
        data class UiState(
            val items: List<SimulationHistoryEntity> = emptyList(),
            val isLoading: Boolean = false,
            val hasMore: Boolean = true,
            val error: String? = null,
        )

        private val _uiState = MutableStateFlow(UiState())
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        private var currentPage = 0
        private val pageSize = 20

        init {
            loadFirstPage()
        }

        fun loadFirstPage() {
            viewModelScope.launch {
                _uiState.value = UiState(isLoading = true)
                runCatching { simulationRepository.listHistoryPaged(0, pageSize) }
                    .onSuccess { page ->
                        currentPage = 0
                        _uiState.value =
                            UiState(
                                items = page,
                                isLoading = false,
                                hasMore = page.size >= pageSize,
                            )
                    }.onFailure { error ->
                        _uiState.value =
                            UiState(
                                isLoading = false,
                                hasMore = false,
                                error = error.message ?: "Falha ao carregar histórico.",
                            )
                    }
            }
        }

        fun loadNextPage() {
            val state = _uiState.value
            if (state.isLoading || !state.hasMore) return

            viewModelScope.launch {
                _uiState.value = state.copy(isLoading = true, error = null)
                val nextPage = currentPage + 1
                runCatching { simulationRepository.listHistoryPaged(nextPage, pageSize) }
                    .onSuccess { page ->
                        currentPage = nextPage
                        _uiState.value =
                            _uiState.value.copy(
                                items = _uiState.value.items + page,
                                isLoading = false,
                                hasMore = page.size >= pageSize,
                            )
                    }.onFailure { error ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                error = error.message ?: "Falha ao carregar próxima página.",
                            )
                    }
            }
        }

        fun clearHistory() {
            viewModelScope.launch {
                runCatching { simulationRepository.clearHistory() }
                    .onSuccess { loadFirstPage() }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(error = error.message ?: "Falha ao limpar histórico.")
                    }
            }
        }
    }
