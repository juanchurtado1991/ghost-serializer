package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class SymbolItem(
    val text: String = "",
    val indices: List<Int> = emptyList(),
)
