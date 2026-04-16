package com.ghost.serialization.sample.api

data class BenchmarkResult(
    val ghostTimeMs: Double,
    val ghostAllocatedBytes: Long,
    val moshiTimeMs: Double,
    val moshiAllocatedBytes: Long
)