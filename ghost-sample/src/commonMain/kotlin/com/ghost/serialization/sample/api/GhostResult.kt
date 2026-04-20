package com.ghost.serialization.sample.api

data class EngineResult(
    val name: String,
    val timeMs: Double,
    val memoryBytes: Long,
    val isSupported: Boolean,
    val jankCount: Int = 0
)

data class GhostResult<T>(
    val data: T,
    val networkTimeMs: Double,
    val parseTimeMs: Double,
    val ghostMemoryBytes: Long,
    val ghostJankCount: Int = 0,
    val engineResults: List<EngineResult>
)