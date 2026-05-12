package com.ghost.serialization.sample.api

data class EngineResult(
    val name: String,
    val timeMs: Double,
    val memoryBytes: Long,
    val isSupported: Boolean,
    val jankCount: Int = 0
)
