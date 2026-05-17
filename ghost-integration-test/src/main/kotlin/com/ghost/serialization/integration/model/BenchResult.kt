package com.ghost.serialization.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class BenchResult(
    val nanos: Long,
    val allocBytes: Long,
    val stdevNanos: Long = 0L
)