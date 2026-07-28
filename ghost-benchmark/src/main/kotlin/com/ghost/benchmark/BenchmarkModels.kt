@file:OptIn(ExperimentalStdlibApi::class)

package com.ghost.benchmark

import com.ghost.serialization.integration.model.BenchmarkMetrics
import com.ghost.serialization.integration.model.ComplexResponse
import com.ghost.serialization.integration.model.StressMetrics
import com.squareup.moshi.Moshi
import kotlinx.serialization.json.Json

// ============================================================================
// Data & Configuration Classes
// ============================================================================

/**
 * Active benchmark constants for the current JVM profile ([BenchmarkProfile]).
 *
 * Values are read once at class initialization from `ghost.benchmark.profile`:
 *
 * - **Full** (`full`, default) — baselines in [RegressionCalculator], ±10% tolerance.
 * - **Fast** (`fast`) — same baselines and tolerance, ~5× less work per suite.
 */
internal object BenchmarkStandard {

    private val profile: BenchmarkProfile = BenchmarkProfile.active()

    /** Lowercase name of the active [BenchmarkProfile] (`full` or `fast`). */
    val profileName: String
        get() = profile.name.lowercase()

    /** Global JIT warmup iterations shared by synthetic and Twitter suites. */
    val WARMUP_ITERATIONS: Int
        get() = profile.warmupIterations

    /** Per-suite local warmup iterations immediately before measurement. */
    val LOCAL_WARMUP_ITERATIONS: Int
        get() = profile.localWarmupIterations

    /** Number of synthetic measurement sessions (median aggregation input). */
    val SYNTHETIC_SESSIONS: Int
        get() = profile.syntheticSessions

    /** Batched samples per synthetic session for latency/allocation averaging. */
    val SYNTHETIC_SAMPLES_PER_SESSION: Int
        get() = profile.syntheticSamplesPerSession

    /** Iteration count for Twitter and Ghost-only micro-benchmarks. */
    val MEASUREMENT_RUNS: Int
        get() = profile.measurementRuns

    /** Progress log interval for long [BenchmarkProgress.repeatWithProgress] loops. */
    val PROGRESS_INTERVAL: Int
        get() = profile.progressInterval

    /** Relative degradation tolerance forwarded to [RegressionCalculator.report]. */
    val REGRESSION_TOLERANCE: Double
        get() = profile.regressionTolerance
}

/** Raw per-session results from the synthetic harness before aggregation. */
internal data class SyntheticRunResults(
    val aggregated: BenchmarkSessionResults,
    val listSessions: List<ModeMetrics>,
    val syncSessions: List<ModeMetrics>,
    val writingSessions: List<ModeMetrics>,
)

/** Shared JSON engine instances (KotlinX Serialization, Moshi) for synthetic and Twitter suites. */
internal class BenchmarkEngines {
    val kJson = Json { ignoreUnknownKeys = true }
    val moshi: Moshi = createBenchmarkMoshi()
    val complexResponseAdapter = moshi.adapter(ComplexResponse::class.java)
}

/** Aggregated synthetic metrics across LIST, SYNC, WRITING, stress, and failure workloads. */
internal data class BenchmarkSessionResults(
    val listMedium: ModeMetrics,
    val syncLarge: ModeMetrics,
    val writing: ModeMetrics,
    val stress: StressMetrics,
    val failure: BenchmarkMetrics
)

/** Ghost / KSER / Moshi metrics for one I/O mode (string, bytes, or streaming). */
internal data class ModeMetrics(
    val string: BenchmarkMetrics,
    val bytes: BenchmarkMetrics,
    val streaming: BenchmarkMetrics
)
