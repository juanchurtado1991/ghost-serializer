@file:OptIn(ExperimentalStdlibApi::class)

package com.ghost.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ghost.serialization.integration.model.BenchmarkMetrics
import com.ghost.serialization.integration.model.ComplexResponse
import com.ghost.serialization.integration.model.StressMetrics
import com.google.gson.Gson
import kotlinx.serialization.json.Json

// ============================================================================
// Data & Configuration Classes
// ============================================================================

/**
 * Active benchmark constants for the current JVM profile ([BenchmarkProfile]).
 *
 * **Full** (`ghost.benchmark.profile=full`, default): baselines in [RegressionCalculator], ±10%.
 *
 * **Fast** (`ghost.benchmark.profile=fast`): same baselines, ±10% tolerance, ~5× less work.
 */
internal object BenchmarkStandard {

    private val profile: BenchmarkProfile = BenchmarkProfile.active()

    val profileName: String
        get() = profile.name.lowercase()

    val WARMUP_ITERATIONS: Int
        get() = profile.warmupIterations

    val LOCAL_WARMUP_ITERATIONS: Int
        get() = profile.localWarmupIterations

    val SYNTHETIC_SESSIONS: Int
        get() = profile.syntheticSessions

    val SYNTHETIC_SAMPLES_PER_SESSION: Int
        get() = profile.syntheticSamplesPerSession

    val MEASUREMENT_RUNS: Int
        get() = profile.measurementRuns

    val PROGRESS_INTERVAL: Int
        get() = profile.progressInterval

    val REGRESSION_TOLERANCE: Double
        get() = profile.regressionTolerance
}

internal data class SyntheticRunResults(
    val aggregated: BenchmarkSessionResults,
    val listSessions: List<ModeMetrics>,
    val syncSessions: List<ModeMetrics>,
    val writingSessions: List<ModeMetrics>,
)

internal class BenchmarkEngines {
    val gson = Gson()
    val kJson = Json { ignoreUnknownKeys = true }
    val jackson: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
}

internal data class BenchmarkSessionResults(
    val listMedium: ModeMetrics,
    val syncLarge: ModeMetrics,
    val writing: ModeMetrics,
    val stress: StressMetrics,
    val failure: BenchmarkMetrics
)

internal data class ModeMetrics(
    val string: BenchmarkMetrics,
    val bytes: BenchmarkMetrics,
    val streaming: BenchmarkMetrics
)
