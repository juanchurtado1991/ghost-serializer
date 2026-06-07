@file:OptIn(ExperimentalStdlibApi::class)

package com.ghost.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ghost.serialization.integration.model.BenchmarkMetrics
import com.ghost.serialization.integration.model.ComplexResponse
import com.ghost.serialization.integration.model.StressMetrics
import com.google.gson.Gson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.serialization.json.Json

// ============================================================================
// Data & Configuration Classes
// ============================================================================

internal data class BenchmarkConfig(val runs: Int, val noTests: Boolean, val warmupIters: Int) {
    companion object {
        fun fromArgs(args: Array<String>): BenchmarkConfig {
            val runs = args.indexOf("--runs")
                .let { if (it != -1 && it + 1 < args.size) args[it + 1].toIntOrNull() ?: 100 else 100 }
            val noTests = args.contains("--no-tests")
            val warmupIters = args.indexOf("--warmup")
                .let {
                    if (it != -1 && it + 1 < args.size) args[it + 1].toIntOrNull()
                        ?: 5000 else 5000
                }
            return BenchmarkConfig(runs, noTests, warmupIters)
        }
    }
}

internal class BenchmarkEngines {
    val gson = Gson()
    val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val moshiAdapter: JsonAdapter<ComplexResponse> = moshi.adapter()
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
