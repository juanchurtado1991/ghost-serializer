package com.ghost.serialization.sample.api

data class GhostResult<T>(
    val data: T,
    val networkTimeMs: Double,
    val parseTimeMs: Double,
    val moshiTimeMs: Double,
    val ghostMemoryBytes: Long,
    val moshiMemoryBytes: Long
)