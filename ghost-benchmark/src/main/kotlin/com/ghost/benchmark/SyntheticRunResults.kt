package com.ghost.benchmark

/** Raw per-session results from the synthetic harness before aggregation. */
internal data class SyntheticRunResults(
    val aggregated: BenchmarkSessionResults,
    val listSessions: List<ModeMetrics>,
    val syncSessions: List<ModeMetrics>,
    val writingSessions: List<ModeMetrics>,
)
