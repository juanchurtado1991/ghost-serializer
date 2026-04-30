package com.ghost.serialization.sample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.serialization.Ghost
import com.ghost.serialization.sample.api.RickAndMortyRepository
import com.ghost.serialization.sample.ui.JankTracker
import com.ghost.serialization.sample.ui.model.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val repository = RickAndMortyRepository()
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init { Ghost.prewarm() }


    fun updatePageCount(count: Float) {
        _uiState.update { it.copy(pageCount = count) }
    }

    fun runBenchmark(jankTracker: JankTracker) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, loadingStatus = "Initiating...") }
        
        viewModelScope.launch {
            // 1. Fetch current data for the UI using Ghost
            try {
                val characters = repository.fetchCharacters(1) // Always use Ghost for initial fetch
                _uiState.update { it.copy(characters = characters.results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Fetch Error: ${e.message}", isLoading = false) }
                return@launch
            }

            // 2. Run the comparison
            repository.runBenchmark(_uiState.value.pageCount.toInt(), jankTracker) { status ->
                _uiState.update { it.copy(loadingStatus = status) }
            }
                .onSuccess { result ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        results = listOf(
                            com.ghost.serialization.sample.api.EngineResult(
                                "GHOST PURE", 
                                result.parseTimeMs, 
                                result.ghostMemoryBytes, 
                                true, 
                                result.ghostJankCount
                            )
                        ) + result.engineResults
                    ) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = "Benchmark Error: ${error.message}", isLoading = false) }
                }
        }
    }
}
