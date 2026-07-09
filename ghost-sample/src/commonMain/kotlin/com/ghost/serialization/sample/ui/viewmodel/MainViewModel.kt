package com.ghost.serialization.sample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.serialization.sample.api.RickAndMortyRepository
import com.ghost.serialization.sample.ui.model.BenchmarkUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val repository = RickAndMortyRepository()

    private val _Benchmark_uiState = MutableStateFlow(BenchmarkUiState())
    val uiState = _Benchmark_uiState.asStateFlow()

    fun updatePageCount(value: Float) {
        _Benchmark_uiState.update { it.copy(pageCount = value) }
    }

    fun toggleRawJsonViewer() {
        _Benchmark_uiState.update { it.copy(showRawJson = !it.showRawJson) }
    }

    fun runBenchmark() {
        _Benchmark_uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                results = emptyList(),
                loadingStatus = "Initiating..."
            )
        }

        viewModelScope.launch {
            try {
                val characters = repository.fetchCharacters(1)
                _Benchmark_uiState.update { it.copy(characters = characters.results) }
            } catch (_: Exception) {
                // Non-fatal: continue benchmark even if UI fetch fails
            }

            val result = repository.runBenchmark(
                pageCount = _Benchmark_uiState.value.pageCount.toInt(),
                onStatusChange = { msg ->
                    _Benchmark_uiState.update { state ->
                        state.copy(
                            loadingStatus = msg,
                            sessionHistory = state.sessionHistory + msg
                        )
                    }
                }
            )

            _Benchmark_uiState.update { state ->
                val pair = result.getOrNull()
                state.copy(
                    isLoading = false,
                    results = pair?.second ?: emptyList(),
                    rawJson = pair?.first ?: "",
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    companion object {
        private const val BENCHMARK_ITERATIONS = 100
    }
}
