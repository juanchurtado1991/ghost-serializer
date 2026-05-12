package com.ghost.serialization.sample.ui.model

import com.ghost.serialization.sample.api.EngineResult
import com.ghost.serialization.sample.model.GhostCharacter

data class UiState(
    val characters: List<GhostCharacter> = emptyList(),
    val isLoading: Boolean = false,
    val loadingStatus: String = "PROFILING...",
    val errorMessage: String? = null,
    val results: List<EngineResult> = emptyList(),
    val sessionHistory: List<String> = emptyList(),
    val pageCount: Float = 5f,
    val isStackDialogVisible: Boolean = false
)
