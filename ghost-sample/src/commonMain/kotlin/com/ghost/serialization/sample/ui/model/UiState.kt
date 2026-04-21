package com.ghost.serialization.sample.ui.model

import com.ghost.serialization.sample.api.EngineResult
import com.ghost.serialization.benchmark.GhostCharacter

enum class NetworkStack(val title: String, val description: String, val engineName: String) {
    GHOST_KTOR(
        "GHOST + KTOR",
        "Multiplatform streaming power. Zero reflection, maximum speed on all targets.",
        "GHOST"
    ),
    KTOR_KOTLINX(
        "KTOR + KOTLINX",
        "The KMP standard. Reliable and type-safe using Kotlinx.Serialization.",
        "KSER"
    ),
    KTORFIT_KOTLINX(
        "KTORFIT + KOTLINX",
        "Popular Retrofit-like interface for KMP. Powered by Ktor and KSP.",
        "KSER"
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
