package com.ghost.serialization.sample.ui.model

import com.ghost.serialization.sample.api.EngineResult

data class YamlUiState(
    val userCount: Float = 500f,
    val isLoading: Boolean = false,
    val loadingStatus: String = "",
    val errorMessage: String? = null,
    val results: List<EngineResult> = emptyList(),
    val sessionHistory: List<String> = emptyList(),
    val previewYaml: String = ""
)
