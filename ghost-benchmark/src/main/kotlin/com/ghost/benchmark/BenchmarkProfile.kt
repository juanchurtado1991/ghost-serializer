package com.ghost.benchmark

/**
 * Benchmark workload shape. Selected via JVM property `ghost.benchmark.profile`:
 *
 * - `full` (default) — regression baselines, ±10% tolerance, ~9 min combined gate.
 * - `fast` — fewer warmup/session iterations, ±10% tolerance, ~1–2 min combined gate.
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
    FULL(
        warmupIterations = 10_000,
        localWarmupIterations = 500,
        syntheticSessions = 500,
        syntheticSamplesPerSession = 50,
        measurementRuns = 5_000,
        progressInterval = 500,
        regressionTolerance = RegressionCalculator.DEFAULT_TOLERANCE,
    ),
    FAST(
        warmupIterations = 2_000,
        localWarmupIterations = 100,
        syntheticSessions = 100,
        syntheticSamplesPerSession = 50,
        measurementRuns = 1_000,
        progressInterval = 100,
        regressionTolerance = RegressionCalculator.DEFAULT_TOLERANCE,
    ),
    ;

    companion object {
        private const val PROPERTY_KEY = "ghost.benchmark.profile"

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
