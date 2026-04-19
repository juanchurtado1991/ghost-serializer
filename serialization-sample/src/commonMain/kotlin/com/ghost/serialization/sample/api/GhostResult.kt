package com.ghostserializer.sample.api

data class EngineResult(
    val name: String,
    val timeMs: Double,
    val memoryBytes: Long,
    val isSupported: Boolean
)

data class GhostResult<T>(
    val data: T,
    val networkTimeMs: Double,
    val parseTimeMs: Double, // This remains for Ghost's specific benchmark logic
    val ghostMemoryBytes: Long,
    val engineResults: List<EngineResult>
)