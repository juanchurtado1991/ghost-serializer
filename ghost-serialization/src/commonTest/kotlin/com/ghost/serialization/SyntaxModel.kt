package com.ghost.serialization

data class SyntaxModel(
    val id: Int,
    val name: String,
    val tags: List<String> = emptyList(),
    val scores: IntArray = intArrayOf()
)
