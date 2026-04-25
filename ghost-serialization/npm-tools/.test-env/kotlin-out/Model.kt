package com.ghost.serialization.standalone

@com.ghost.serialization.annotations.GhostSerialization
data class Model(
    val id: Int = 0,
    val tags: List<String> = emptyList()
)

