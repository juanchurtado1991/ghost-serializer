package com.ghost.serialization.sample.api

data class BenchmarkResult(
    val timeMs: Double,
    val allocatedBytes: Long
)