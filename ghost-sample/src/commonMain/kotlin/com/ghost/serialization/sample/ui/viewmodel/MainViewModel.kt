package com.ghost.serialization.sample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.serialization.sample.api.RickAndMortyRepository
import com.ghost.serialization.sample.ui.model.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ghost.serialization.sample.util.format

class MainViewModel : ViewModel() {
    private val repository = RickAndMortyRepository()

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    fun updatePageCount(count: Float) {
        _uiState.update { it.copy(pageCount = count) }
    }

    fun runBenchmark() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, results = emptyList(), loadingStatus = "Initiating...") }

        viewModelScope.launch {
            // Fetch characters for UI display
            try {
                val characters = repository.fetchCharacters(1)
                _uiState.update { it.copy(characters = characters.results) }
            } catch (e: Exception) {
                // Non-fatal: continue benchmark even if UI fetch fails
            }

            repository.runBenchmark(
                pageCount = _uiState.value.pageCount.toInt(),
                onStatusChange = { status ->
                    _uiState.update { it.copy(loadingStatus = status) }
                }
            ).onSuccess { results ->
                val logEntry = buildString {
                    appendLine("--- RUN (${_uiState.value.pageCount.toInt()} pages x${BENCHMARK_ITERATIONS}) ---")
                    results.forEach { r ->
                        appendLine("${r.name}: ${"%.3f".format(r.timeMs)}ms | ${r.memoryBytes / 1024}KB")
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        results = results,
                        sessionHistory = state.sessionHistory + logEntry
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message, isLoading = false) }
            }
        }
    }

    companion object {
        private const val BENCHMARK_ITERATIONS = 100
    }
}
