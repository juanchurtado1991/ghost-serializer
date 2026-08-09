package com.ghost.serialization

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class SyntaxModel(
    val id: Int,
    val name: String,
    val tags: List<String> = emptyList(),
    val scores: IntArray = intArrayOf()
)
