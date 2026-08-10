package com.ghost.benchmark

/**
 * Benchmark workload shape selected via JVM property `ghost.benchmark.profile`.
 *
 * - [FULL] (default) — **source of truth** for README baselines and release/merge gates (±10%).
 * - [FAST] — smoke gate only (±20%, ~5× fewer iterations). Prefer [FULL] when results disagree.
 */
internal enum class BenchmarkProfile(
    val warmupIterations: Int,
    val localWarmupIterations: Int,
    val syntheticSessions: Int,
    val syntheticSamplesPerSession: Int,
    val measurementRuns: Int,
    val progressInterval: Int,
    val regressionTolerance: Double,
) {
    /** Production regression profile — README baselines; **source of truth** for release gates. */
    FULL(
        warmupIterations = 10_000,
        localWarmupIterations = 500,
        syntheticSessions = 500,
        syntheticSamplesPerSession = 50,
        measurementRuns = 5_000,
        progressInterval = 500,
        regressionTolerance = RegressionCalculator.DEFAULT_TOLERANCE,
    ),

    /**
     * Developer / smoke gate — fewer warmup and session iterations.
     * Wider speed tolerance absorbs Ghost÷KSER ratio noise; [FULL] remains the release bar.
     */
    FAST(
        warmupIterations = 2_000,
        localWarmupIterations = 100,
        syntheticSessions = 100,
        syntheticSamplesPerSession = 50,
        measurementRuns = 1_000,
        progressInterval = 100,
        regressionTolerance = FAST_REGRESSION_TOLERANCE,
    ),
    ;

    companion object {
        private const val PROPERTY_KEY = "ghost.benchmark.profile"

        /** Relative Ghost÷KSER advantage degradation tolerated on the fast profile only. */
        const val FAST_REGRESSION_TOLERANCE: Double = 0.20

        /** Returns [FULL] unless `ghost.benchmark.profile=fast`. */
        fun active(): BenchmarkProfile {
            return when (System.getProperty(PROPERTY_KEY, FULL_NAME)) {
                FAST_NAME -> FAST
                else -> FULL
            }
        }

        private const val FULL_NAME = "full"
        private const val FAST_NAME = "fast"
    }
}
