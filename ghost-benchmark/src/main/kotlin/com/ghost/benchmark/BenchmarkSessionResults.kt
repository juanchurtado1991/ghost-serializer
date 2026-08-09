package com.ghost.benchmark

import com.ghost.serialization.integration.model.BenchmarkMetrics
import com.ghost.serialization.integration.model.StressMetrics

/** Aggregated synthetic metrics across LIST, SYNC, WRITING, stress, and failure workloads. */
internal data class BenchmarkSessionResults(
    val listMedium: ModeMetrics,
    val syncLarge: ModeMetrics,
    val writing: ModeMetrics,
    val stress: StressMetrics,
    val failure: BenchmarkMetrics
)
