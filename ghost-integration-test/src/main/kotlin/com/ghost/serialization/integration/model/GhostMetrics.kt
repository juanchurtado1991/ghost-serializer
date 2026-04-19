package com.ghost.serialization.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class BenchResult(val nanos: Long, val allocBytes: Long)

@Serializable
data class BenchmarkMetrics(
    val gson: BenchResult,
    val moshi: BenchResult,
    val kser: BenchResult,
    val ghost: BenchResult
)

@Serializable
data class StressMetrics(
    val nesting: BenchmarkMetrics,
    val large: BenchmarkMetrics
)

@Serializable
data class GhostMetrics(
    val cold: BenchmarkMetrics,
    val steady: BenchmarkMetrics,
    val serialization: BenchmarkMetrics,
    val stress: StressMetrics,
    val failure: BenchmarkMetrics
)
