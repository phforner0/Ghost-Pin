package com.ghostpin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostpin.app.diagnostics.RealismDiagnosticsInput
import com.ghostpin.app.diagnostics.RealismDiagnosticsResult
import com.ghostpin.app.diagnostics.RealismDiagnosticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RealismDiagnosticsUiState {
    data object Idle : RealismDiagnosticsUiState

    data object Loading : RealismDiagnosticsUiState

    data class Success(
        val result: RealismDiagnosticsResult,
    ) : RealismDiagnosticsUiState

    data class Error(
        val message: String,
    ) : RealismDiagnosticsUiState
}

@HiltViewModel
class RealismDiagnosticsViewModel
    @Inject
    constructor(
        private val useCase: RealismDiagnosticsUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<RealismDiagnosticsUiState>(RealismDiagnosticsUiState.Idle)
        val uiState: StateFlow<RealismDiagnosticsUiState> = _uiState.asStateFlow()

        fun analyze(input: RealismDiagnosticsInput) {
            viewModelScope.launch {
                _uiState.value = RealismDiagnosticsUiState.Loading
                useCase
                    .analyze(input)
                    .onSuccess { result ->
                        _uiState.value = RealismDiagnosticsUiState.Success(result)
                    }.onFailure { error ->
                        _uiState.value =
                            RealismDiagnosticsUiState.Error(
                                error.message ?: "Failed to generate realism diagnostics."
                            )
                    }
            }
        }
    }
