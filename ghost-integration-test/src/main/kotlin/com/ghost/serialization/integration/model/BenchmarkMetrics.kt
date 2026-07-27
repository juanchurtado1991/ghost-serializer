package com.ghost.serialization.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkMetrics(
    val ghost: BenchResult,
    val kser: BenchResult,
    val moshi: BenchResult,
)
