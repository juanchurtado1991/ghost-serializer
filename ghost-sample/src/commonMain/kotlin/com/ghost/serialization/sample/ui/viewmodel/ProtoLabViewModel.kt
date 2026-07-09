package com.ghost.serialization.sample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.serialization.sample.api.GoogleBooksRepository
import com.ghost.serialization.sample.ui.model.ProtoLabUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProtoLabViewModel : ViewModel() {

    private val repository = GoogleBooksRepository()

    private val _uiState = MutableStateFlow(ProtoLabUiState())
    val uiState = _uiState.asStateFlow()

    fun selectTopic(topic: String) {
        _uiState.update { it.copy(selectedTopic = topic) }
        runBenchmark(topic)
    }

    fun runBenchmark(query: String = _uiState.value.selectedTopic) {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                benchmarkResults = emptyList(),
                loadingStatus = "Initiating..."
            )
        }

        viewModelScope.launch {
            repository.fetchAndBenchmark(
                query = query,
                onStatusChange = { status ->
                    _uiState.update { it.copy(loadingStatus = status) }
                }
            ).onSuccess { result ->
                val logEntry = buildString {
                    appendLine("--- PROTO LAB: \"$query\" ---")
                    result.benchmarkResults.forEach { engineResult ->
                        val suffix = if (engineResult.timeMs < 0) " FAILED" else
                            ": ${"%.3f".format(engineResult.timeMs)}ms | ${engineResult.memoryBytes / 1024}KB"
                        appendLine("${engineResult.name}$suffix")
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        books = result.books,
                        benchmarkResults = result.benchmarkResults,
                        standardJson = result.standardJson,
                        proto3Json = result.proto3Json,
                        sessionHistory = state.sessionHistory + logEntry
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun toggleRawJson() {
        _uiState.update { it.copy(showRawJson = !it.showRawJson) }
    }

    fun toggleProto3View() {
        _uiState.update { it.copy(showProto3Json = !it.showProto3Json) }
    }

    fun copySessionLogs(): String = _uiState.value.sessionHistory.joinToString("\n")
}
