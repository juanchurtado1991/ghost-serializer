package com.ghost.serialization.sample.ui.model

import com.ghost.serialization.sample.api.EngineResult
import com.ghost.serialization.sample.domain.GhostCharacter

enum class NetworkStack(val title: String, val description: String) {
    GHOST_KTOR(
        "GHOST + KTOR",
        "Multiplatform streaming power. Zero reflection, maximum speed on all targets."
    ),
    KTOR_KOTLINX(
        "KTOR + KOTLINX",
        "The KMP standard. Reliable and type-safe using Kotlinx.Serialization."
    ),
    KTORFIT_KOTLINX(
        "KTORFIT + KOTLINX",
        "Popular Retrofit-like interface for KMP. Powered by Ktor and KSP."
    )
}

data class UiState(
    val characters: List<GhostCharacter> = emptyList(),
    val isLoading: Boolean = false,
    val loadingStatus: String = "PROFILING...",
    val errorMessage: String? = null,
    val results: List<EngineResult> = emptyList(),
    val sessionHistory: List<String> = emptyList(),
    val pageCount: Float = 20f,
    val selectedStack: NetworkStack = NetworkStack.GHOST_KTOR,
    val isStackDialogVisible: Boolean = false
)
