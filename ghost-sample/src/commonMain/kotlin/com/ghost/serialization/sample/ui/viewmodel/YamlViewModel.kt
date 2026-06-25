package com.ghost.serialization.sample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.serialization.sample.api.YamlBenchmarkRepository
import com.ghost.serialization.sample.ui.model.YamlUiState
import com.ghost.serialization.sample.util.format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class YamlViewModel : ViewModel() {

    private val repository = YamlBenchmarkRepository()

    private val _uiState = MutableStateFlow(YamlUiState())
    val uiState = _uiState.asStateFlow()

    fun updateUserCount(count: Float) {
        _uiState.update { it.copy(userCount = count) }
    }

    fun runBenchmark() {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                results = emptyList(),
                previewYaml = "",
                loadingStatus = "Initiating YAML benchmark..."
            )
        }

        viewModelScope.launch {
            repository.runBenchmark(
                userCount = _uiState.value.userCount.toInt(),
                onStatusChange = { status ->
                    _uiState.update { it.copy(loadingStatus = status) }
                },
                onPreviewReady = { yaml ->
                    _uiState.update { it.copy(previewYaml = yaml) }
                }
            ).onSuccess { benchmarkResult ->
                val logEntry = buildString {
                    appendLine("--- YAML RUN (${_uiState.value.userCount.toInt()} users x$BENCHMARK_ITERATIONS) ---")
                    benchmarkResult.engineResults.forEach { result ->
                        appendLine(
                            "${result.name}: ${"%.3f".format(result.timeMs)}ms | ${result.memoryBytes / 1024}KB"
                        )
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        results = benchmarkResult.engineResults,
                        previewYaml = benchmarkResult.previewYaml,
                        sessionHistory = state.sessionHistory + logEntry
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = error.message ?: "Unknown error",
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
