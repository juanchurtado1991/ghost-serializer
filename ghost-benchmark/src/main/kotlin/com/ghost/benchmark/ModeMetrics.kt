package com.ghost.benchmark

import com.ghost.serialization.integration.model.BenchmarkMetrics

/** Ghost / KSER / Moshi metrics for one I/O mode (string, bytes, or streaming). */
internal data class ModeMetrics(
    val string: BenchmarkMetrics,
    val bytes: BenchmarkMetrics,
    val streaming: BenchmarkMetrics
)
