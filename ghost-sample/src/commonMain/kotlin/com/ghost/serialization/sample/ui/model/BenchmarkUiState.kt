package com.ghost.serialization.sample.ui.model

import com.ghost.serialization.sample.model.EngineResult
import com.ghost.serialization.sample.model.GhostCharacter

data class BenchmarkUiState(
    val characters: List<GhostCharacter> = emptyList(),
    val isLoading: Boolean = false,
    val loadingStatus: String = "",
    val errorMessage: String? = null,
    val results: List<EngineResult> = emptyList(),
    val sessionHistory: List<String> = emptyList(),
    val pageCount: Float = 20f,
    val rawJson: String = "",
    val showRawJson: Boolean = false
)
