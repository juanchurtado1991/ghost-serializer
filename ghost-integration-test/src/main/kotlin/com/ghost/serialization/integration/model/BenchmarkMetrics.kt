package com.ghost.serialization.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkMetrics(
    val gson: BenchResult,
    val moshi: BenchResult,
    val kser: BenchResult,
    val jackson: BenchResult,
    val ghost: BenchResult
)
