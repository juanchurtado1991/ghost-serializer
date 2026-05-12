package com.ghost.serialization.sample.api

data class GhostResult<T>(
    val data: T,
    val networkTimeMs: Double,
    val parseTimeMs: Double,
    val ghostMemoryBytes: Long,
    val ghostJankCount: Int = 0,
    val engineResults: List<EngineResult>
)