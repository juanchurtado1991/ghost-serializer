package com.ghost.serialization.sample.ui.model

import com.ghost.serialization.sample.api.EngineResult
import com.ghost.serialization.sample.model.OpenLibraryBook

data class ProtoLabUiState(
    val books: List<OpenLibraryBook> = emptyList(),
    val isLoading: Boolean = false,
    val loadingStatus: String = "",
    val errorMessage: String? = null,
    // Benchmark results (same methodology as main tab: 100 iterations, GC leveled, thread-local allocation tracking)
    val benchmarkResults: List<EngineResult> = emptyList(),
    val sessionHistory: List<String> = emptyList(),
    // Raw payloads for the viewer — standard REST JSON and the synthesized proto3 JSON variant
    val standardJson: String = "",
    val proto3Json: String = "",
    // Which payload is currently shown in the Raw JSON viewer
    val showProto3Json: Boolean = false,
    val showRawJson: Boolean = false,
    val selectedTopic: String = "Kotlin"
)
