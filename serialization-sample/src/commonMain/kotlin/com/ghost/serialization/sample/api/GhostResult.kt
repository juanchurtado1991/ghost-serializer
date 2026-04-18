package com.ghost.serialization.sample.api

data class GhostResult<T>(
    val data: T,
    val networkTimeMs: Double,
    val parseTimeMs: Double,
    val moshiTimeMs: Double,
    val kserTimeMs: Double = -1.0,
    val ghostMemoryBytes: Long,
    val moshiMemoryBytes: Long,
    val kserMemoryBytes: Long = 0L
)