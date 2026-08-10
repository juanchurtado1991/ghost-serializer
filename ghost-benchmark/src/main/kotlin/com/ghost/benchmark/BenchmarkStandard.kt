package com.ghost.benchmark

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
