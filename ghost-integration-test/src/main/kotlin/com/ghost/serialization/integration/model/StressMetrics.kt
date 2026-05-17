package com.ghost.serialization.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class StressMetrics(
    val nesting: BenchmarkMetrics,
    val large: BenchmarkMetrics
)