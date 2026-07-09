package com.ghost.serialization.sample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.serialization.sample.api.RickAndMortyRepository
import com.ghost.serialization.sample.ui.model.BenchmarkUiState
import com.ghost.serialization.sample.util.format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val repository = RickAndMortyRepository()

    private val _Benchmark_uiState = MutableStateFlow(BenchmarkUiState())
    val uiState = _Benchmark_uiState.asStateFlow()

    fun updatePageCount(count: Float) {
        _Benchmark_uiState.update { it.copy(pageCount = count) }
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

            repository.runBenchmark(
                pageCount = _Benchmark_uiState.value.pageCount.toInt(),
                onStatusChange = { status ->
                    _Benchmark_uiState.update { it.copy(loadingStatus = status) }
                }
            ).onSuccess { results ->
                val logEntry = buildString {
                    appendLine(
                        "--- RUN (${
                            _Benchmark_uiState.value.pageCount.toInt()
                        } pages x${
                            BENCHMARK_ITERATIONS
                        }) ---"
                    )
                    results.forEach { r ->
                        appendLine(
                            "${
                                r.name
                            }: ${
                                "%.3f".format(r.timeMs)
                            }ms | ${
                                r.memoryBytes / 1024
                            }KB"
                        )
                    }
                }
                _Benchmark_uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        results = results,
                        sessionHistory = state.sessionHistory + logEntry
                    )
                }
            }.onFailure { error ->
                _Benchmark_uiState.update {
                    it.copy(
                        errorMessage = error.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    companion object {
        private const val BENCHMARK_ITERATIONS = 100
    }
}
