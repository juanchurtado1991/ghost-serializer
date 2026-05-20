@file:OptIn(
    ExperimentalStdlibApi::class, InternalGhostApi::class,
    ExperimentalSerializationApi::class
)
@file:Suppress("SameParameterValue")

package com.ghost.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.BenchResult
import com.ghost.serialization.integration.model.BenchUser
import com.ghost.serialization.integration.model.BenchmarkMetrics
import com.ghost.serialization.integration.model.Category
import com.ghost.serialization.integration.model.ComplexResponse
import com.ghost.serialization.integration.model.ExtremeMetadata
import com.ghost.serialization.integration.model.StressMetrics
import com.ghost.serialization.integration.model.UserRole
import com.google.gson.Gson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.management.ManagementFactory
import kotlin.math.sqrt
import kotlin.system.exitProcess
import com.google.gson.stream.JsonReader as GsonReader
import com.google.gson.stream.JsonWriter as GsonWriter

fun main(args: Array<String>) {
    val config = BenchmarkConfig.fromArgs(args)

    println("\n--- INITIALIZING PERFORMANCE BENCHMARKS (Runs: ${config.runs}) ---")
    val threadBean = initializePlatformDiagnostics() ?: return

    val engines = BenchmarkEngines()

    val smallComplex = generateComplexData(20)
    val smallBytes = generateNeutralJson(smallComplex).encodeUtf8()

    // 1. Cold Start
    runAndPrintColdStart(smallBytes, engines)

    // 2. Warmup
    runWarmupPhase(engines, smallBytes, smallComplex, config.warmupIters)

    // 3. Benchmarking Sessions
    val sessions = runBenchmarkSessions(config.runs, threadBean, engines, smallBytes)

    // 4. Calculate Final Results
    val finalResults = calculateFinalResults(sessions)

    // 5. Print Final Results
    printFinalResults(finalResults, config.runs)

    // 6. Ghost Special Features (exclusive capabilities, no competition)
    GhostSpecialFeaturesBenchmark.run(config.runs, config.warmupIters)

    // 7. Verification Tests
    if (!config.noTests) {
        val testResults = ParsingTestBenchmark.runAllTests()
        ParsingTestBenchmark.printUnifiedSummaryTable(testResults)
    }

    println("\n[COMPLETE] Benchmark execution finished.")
    exitProcess(0)
}

// ============================================================================
// Data & Configuration Classes
// ============================================================================

private data class BenchmarkConfig(val runs: Int, val noTests: Boolean, val warmupIters: Int) {
    companion object {
        fun fromArgs(args: Array<String>): BenchmarkConfig {
            val runs = args.indexOf("--runs")
                .let { if (it != -1 && it + 1 < args.size) args[it + 1].toIntOrNull() ?: 1 else 1 }
            val noTests = args.contains("--no-tests")
            val warmupIters = args.indexOf("--warmup")
                .let {
                    if (it != -1 && it + 1 < args.size) args[it + 1].toIntOrNull()
                        ?: 15000 else 15000
                }
            return BenchmarkConfig(runs, noTests, warmupIters)
        }
    }
}

private class BenchmarkEngines {
    val gson = Gson()
    val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val kJson = Json { ignoreUnknownKeys = true }
    val jackson: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
}

private data class BenchmarkSessionResults(
    val listMedium: ModeMetrics,
    val syncLarge: ModeMetrics,
    val writing: ModeMetrics,
    val stress: StressMetrics,
    val failure: BenchmarkMetrics
)

private data class ModeMetrics(
    val string: BenchmarkMetrics,
    val bytes: BenchmarkMetrics,
    val streaming: BenchmarkMetrics
)

// ============================================================================
// Phase Executors
// ============================================================================

private fun runAndPrintColdStart(smallBytes: ByteString, engines: BenchmarkEngines) {
    val coldMetrics = runColdStart(smallBytes, engines.jackson)
    printRankedTable("COLD START (first parse, before JUnit suite)", coldMetrics)
}

private fun runWarmupPhase(
    engines: BenchmarkEngines,
    smallBytes: ByteString,
    smallComplex: ComplexResponse,
    warmupIters: Int
) {
    println("\n🔥 Warming up JIT ($warmupIters iterations × 3 modes)...")
    val jsonString = smallBytes.utf8()
    val rawBytes = smallBytes.toByteArray()
    val moshiAdapter = engines.moshi.adapter<ComplexResponse>()

    repeat(warmupIters) {
        warmupStringMode(
            engines.gson,
            moshiAdapter,
            engines.kJson,
            engines.jackson,
            jsonString,
            smallComplex
        )
        warmupBytesMode(
            engines.gson,
            moshiAdapter,
            engines.kJson,
            engines.jackson,
            rawBytes,
            smallComplex
        )
        warmupStreamingMode(
            engines.gson,
            moshiAdapter,
            engines.kJson,
            engines.jackson,
            rawBytes,
            smallComplex
        )
    }
}

private fun runBenchmarkSessions(
    runs: Int,
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    smallBytes: ByteString
): List<BenchmarkSessionResults> {
    val sessions = mutableListOf<BenchmarkSessionResults>()

    repeat(runs) { i ->
        if (runs > 1) println("[Run ${i + 1}/$runs] Benchmarking...")

        val listMediumMetrics = runListMediumDeserialization(threadBean, engines)
        val syncLargeMetrics = runSyncLargeDeserialization(threadBean, engines)
        val writingMetrics = runSerialization(threadBean, engines)
        val stressMetrics =
            runStressTests(engines.gson, engines.moshi, engines.kJson, engines.jackson)
        val failureMetrics = runFailureTests(smallBytes)

        sessions.add(
            BenchmarkSessionResults(
                listMedium = listMediumMetrics,
                syncLarge = syncLargeMetrics,
                writing = writingMetrics,
                stress = stressMetrics,
                failure = failureMetrics
            )
        )
    }
    return sessions
}

private fun runListMediumDeserialization(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines
): ModeMetrics {
    val complex = generateComplexData(200)
    val bytes = generateNeutralJson(complex).encodeUtf8()
    return runDeserializationAllModes(
        threadBean,
        engines.gson,
        engines.moshi,
        engines.kJson,
        engines.jackson,
        bytes
    )
}

private fun runSyncLargeDeserialization(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines
): ModeMetrics {
    val complex = generateComplexData(2000)
    val bytes = generateNeutralJson(complex).encodeUtf8()
    return runDeserializationAllModes(
        threadBean,
        engines.gson,
        engines.moshi,
        engines.kJson,
        engines.jackson,
        bytes
    )
}

private fun runSerialization(threadBean: ThreadMXBean, engines: BenchmarkEngines): ModeMetrics {
    val complex = generateComplexData(1000)
    return runSerializationAllModes(
        threadBean,
        engines.gson,
        engines.moshi,
        engines.kJson,
        engines.jackson,
        complex
    )
}

// ============================================================================
// Warmup Details
// ============================================================================

private fun warmupStringMode(
    gson: Gson,
    moshiAdapter: JsonAdapter<ComplexResponse>,
    kJson: Json,
    jackson: ObjectMapper,
    jsonString: String,
    complex: ComplexResponse
) {
    gson.fromJson(jsonString, ComplexResponse::class.java)
    moshiAdapter.fromJson(jsonString)
    kJson.decodeFromString<ComplexResponse>(jsonString)
    jackson.readValue<ComplexResponse>(jsonString)
    Ghost.deserialize<ComplexResponse>(jsonString)

    gson.toJson(complex)
    moshiAdapter.toJson(complex)
    kJson.encodeToString(complex)
    jackson.writeValueAsString(complex)
    Ghost.encodeToString(complex)
}

private fun warmupBytesMode(
    gson: Gson,
    moshiAdapter: JsonAdapter<ComplexResponse>,
    kJson: Json,
    jackson: ObjectMapper,
    rawBytes: ByteArray,
    complex: ComplexResponse
) {
    val stringFromBytes = String(rawBytes, Charsets.UTF_8)
    gson.fromJson(stringFromBytes, ComplexResponse::class.java)
    moshiAdapter.fromJson(stringFromBytes)
    kJson.decodeFromString<ComplexResponse>(stringFromBytes)
    jackson.readValue<ComplexResponse>(rawBytes)
    Ghost.deserialize<ComplexResponse>(rawBytes)

    gson.toJson(complex).toByteArray()
    moshiAdapter.toJson(complex).toByteArray()
    kJson.encodeToString(complex).toByteArray()
    jackson.writeValueAsBytes(complex)
    Ghost.encodeToBytes(complex)
}

@Suppress("CheckResult")
private fun warmupStreamingMode(
    gson: Gson,
    moshiAdapter: JsonAdapter<ComplexResponse>,
    kJson: Json,
    jackson: ObjectMapper,
    rawBytes: ByteArray,
    complex: ComplexResponse
) {
    gson.fromJson<ComplexResponse>(
        GsonReader(InputStreamReader(ByteArrayInputStream(rawBytes))),
        ComplexResponse::class.java
    )
    moshiAdapter.fromJson(Buffer().write(rawBytes))
    kJson.decodeFromBufferedSource<ComplexResponse>(Buffer().write(rawBytes))
    jackson.readValue<ComplexResponse>(ByteArrayInputStream(rawBytes))
    Ghost.deserialize<ComplexResponse>(Buffer().write(rawBytes))

    ByteArrayOutputStream().also { os ->
        GsonWriter(OutputStreamWriter(os)).use { w ->
            gson.toJson(complex, ComplexResponse::class.java, w)
        }
    }
    Buffer().also { moshiAdapter.toJson(it, complex) }
    Buffer().also { kJson.encodeToBufferedSink(complex, it) }
    ByteArrayOutputStream().also { jackson.writeValue(it, complex) }
    Buffer().also { Ghost.serialize(it, complex) }
}

// ============================================================================
// Core Execution Logic
// ============================================================================

@Suppress("CheckResult")
private fun runColdStart(data: ByteString, jackson: ObjectMapper): BenchmarkMetrics {
    val coldGson = Gson()
    val coldMoshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val coldKser = Json { ignoreUnknownKeys = true }

    val gsonTime = measureTimeNanos {
        coldGson.fromJson<ComplexResponse>(
            GsonReader(InputStreamReader(ByteArrayInputStream(data.toByteArray()))),
            ComplexResponse::class.java
        )
    }
    val moshiTime = measureTimeNanos {
        coldMoshi.adapter<ComplexResponse>().fromJson(Buffer().write(data.toByteArray()))
    }
    val kSerializationTime =
        measureTimeNanos { coldKser.decodeFromString<ComplexResponse>(data.utf8()) }
    val jacksonTime = measureTimeNanos { jackson.readValue<ComplexResponse>(data.utf8()) }
    val ghostTime = measureTimeNanos { Ghost.deserialize<ComplexResponse>(data.toByteArray()) }

    return BenchmarkMetrics(
        gson = BenchResult(gsonTime, 0),
        moshi = BenchResult(moshiTime, 0),
        kser = BenchResult(kSerializationTime, 0),
        jackson = BenchResult(jacksonTime, 0),
        ghost = BenchResult(ghostTime, 0)
    )
}

private fun runDeserializationAllModes(
    threadBean: ThreadMXBean,
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    jackson: ObjectMapper,
    data: ByteString
): ModeMetrics {
    val rawBytes = data.toByteArray()
    val jsonString = data.utf8()
    val moshiAdapter = moshi.adapter<ComplexResponse>()

    val stringMetrics =
        measureStringDeserialization(threadBean, gson, moshiAdapter, kJson, jackson, jsonString)
    val bytesMetrics =
        measureBytesDeserialization(threadBean, gson, moshiAdapter, kJson, jackson, rawBytes)
    val streamingMetrics =
        measureStreamingDeserialization(threadBean, gson, moshiAdapter, kJson, jackson, rawBytes)

    return ModeMetrics(stringMetrics, bytesMetrics, streamingMetrics)
}

private fun runSerializationAllModes(
    threadBean: ThreadMXBean,
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    jackson: ObjectMapper,
    complex: ComplexResponse
): ModeMetrics {
    val moshiAdapter = moshi.adapter<ComplexResponse>()

    val stringMetrics =
        measureStringSerialization(threadBean, gson, moshiAdapter, kJson, jackson, complex)
    val bytesMetrics =
        measureBytesSerialization(threadBean, gson, moshiAdapter, kJson, jackson, complex)
    val streamingMetrics =
        measureStreamingSerialization(threadBean, gson, moshiAdapter, kJson, jackson, complex)

    return ModeMetrics(stringMetrics, bytesMetrics, streamingMetrics)
}

// ============================================================================
// Measurement Helpers: Deserialization
// ============================================================================

private fun measureStringDeserialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    moshiAdapter: JsonAdapter<ComplexResponse>,
    kJson: Json,
    jackson: ObjectMapper,
    jsonString: String
): BenchmarkMetrics {
    val gsonTime =
        measurePerf(threadBean) { gson.fromJson(jsonString, ComplexResponse::class.java) }
    val moshiTime = measurePerf(threadBean) { moshiAdapter.fromJson(jsonString) }
    val kserTime = measurePerf(threadBean) { kJson.decodeFromString<ComplexResponse>(jsonString) }
    val jacksonTime = measurePerf(threadBean) { jackson.readValue<ComplexResponse>(jsonString) }
    val ghostTime = measurePerf(threadBean) { Ghost.deserialize<ComplexResponse>(jsonString) }

    return BenchmarkMetrics(
        gson = BenchResult(gsonTime.second, gsonTime.third),
        moshi = BenchResult(moshiTime.second, moshiTime.third),
        kser = BenchResult(kserTime.second, kserTime.third),
        jackson = BenchResult(jacksonTime.second, jacksonTime.third),
        ghost = BenchResult(ghostTime.second, ghostTime.third)
    )
}

private fun measureBytesDeserialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    moshiAdapter: JsonAdapter<ComplexResponse>,
    kJson: Json,
    jackson: ObjectMapper,
    rawBytes: ByteArray
): BenchmarkMetrics {
    val stringFromBytes = String(rawBytes, Charsets.UTF_8)
    val gsonTime =
        measurePerf(threadBean) { gson.fromJson(stringFromBytes, ComplexResponse::class.java) }
    val moshiTime = measurePerf(threadBean) { moshiAdapter.fromJson(stringFromBytes) }
    val kserTime =
        measurePerf(threadBean) { kJson.decodeFromString<ComplexResponse>(stringFromBytes) }
    val jacksonTime = measurePerf(threadBean) { jackson.readValue<ComplexResponse>(rawBytes) }
    val ghostTime = measurePerf(threadBean) { Ghost.deserialize<ComplexResponse>(rawBytes) }

    return BenchmarkMetrics(
        gson = BenchResult(gsonTime.second, gsonTime.third),
        moshi = BenchResult(moshiTime.second, moshiTime.third),
        kser = BenchResult(kserTime.second, kserTime.third),
        jackson = BenchResult(jacksonTime.second, jacksonTime.third),
        ghost = BenchResult(ghostTime.second, ghostTime.third)
    )
}

private fun measureStreamingDeserialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    moshiAdapter: JsonAdapter<ComplexResponse>,
    kJson: Json,
    jackson: ObjectMapper,
    rawBytes: ByteArray
): BenchmarkMetrics {
    val gsonTime = measurePerf(threadBean) {
        gson.fromJson<ComplexResponse>(
            GsonReader(InputStreamReader(ByteArrayInputStream(rawBytes))),
            ComplexResponse::class.java
        )
    }
    val moshiTime = measurePerf(threadBean) { moshiAdapter.fromJson(Buffer().write(rawBytes)) }
    val kserTime = measurePerf(threadBean) {
        kJson.decodeFromBufferedSource<ComplexResponse>(
            Buffer().write(rawBytes)
        )
    }
    val jacksonTime =
        measurePerf(threadBean) { jackson.readValue<ComplexResponse>(ByteArrayInputStream(rawBytes)) }
    val ghostTime =
        measurePerf(threadBean) { Ghost.deserialize<ComplexResponse>(Buffer().write(rawBytes)) }

    return BenchmarkMetrics(
        gson = BenchResult(gsonTime.second, gsonTime.third),
        moshi = BenchResult(moshiTime.second, moshiTime.third),
        kser = BenchResult(kserTime.second, kserTime.third),
        jackson = BenchResult(jacksonTime.second, jacksonTime.third),
        ghost = BenchResult(ghostTime.second, ghostTime.third)
    )
}

// ============================================================================
// Measurement Helpers: Serialization
// ============================================================================

private fun measureStringSerialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    moshiAdapter: JsonAdapter<ComplexResponse>,
    kJson: Json,
    jackson: ObjectMapper,
    complex: ComplexResponse
): BenchmarkMetrics {
    val gsonTime = measurePerf(threadBean) { gson.toJson(complex) }
    val moshiTime = measurePerf(threadBean) { moshiAdapter.toJson(complex) }
    val kserTime = measurePerf(threadBean) { kJson.encodeToString(complex) }
    val jacksonTime = measurePerf(threadBean) { jackson.writeValueAsString(complex) }
    val ghostTime = measurePerf(threadBean) { Ghost.encodeToString(complex) }

    return BenchmarkMetrics(
        gson = BenchResult(gsonTime.second, gsonTime.third),
        moshi = BenchResult(moshiTime.second, moshiTime.third),
        kser = BenchResult(kserTime.second, kserTime.third),
        jackson = BenchResult(jacksonTime.second, jacksonTime.third),
        ghost = BenchResult(ghostTime.second, ghostTime.third)
    )
}

private fun measureBytesSerialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    moshiAdapter: JsonAdapter<ComplexResponse>,
    kJson: Json,
    jackson: ObjectMapper,
    complex: ComplexResponse
): BenchmarkMetrics {
    val gsonTime = measurePerf(threadBean) { gson.toJson(complex).toByteArray() }
    val moshiTime = measurePerf(threadBean) { moshiAdapter.toJson(complex).toByteArray() }
    val kserTime = measurePerf(threadBean) { kJson.encodeToString(complex).toByteArray() }
    val jacksonTime = measurePerf(threadBean) { jackson.writeValueAsBytes(complex) }
    val ghostTime = measurePerf(threadBean) { Ghost.encodeToBytes(complex) }

    return BenchmarkMetrics(
        gson = BenchResult(gsonTime.second, gsonTime.third),
        moshi = BenchResult(moshiTime.second, moshiTime.third),
        kser = BenchResult(kserTime.second, kserTime.third),
        jackson = BenchResult(jacksonTime.second, jacksonTime.third),
        ghost = BenchResult(ghostTime.second, ghostTime.third)
    )
}

private fun measureStreamingSerialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    moshiAdapter: JsonAdapter<ComplexResponse>,
    kJson: Json,
    jackson: ObjectMapper,
    complex: ComplexResponse
): BenchmarkMetrics {
    val gsonTime = measurePerf(threadBean) {
        val os = ByteArrayOutputStream()
        with(GsonWriter(OutputStreamWriter(os))) {
            gson.toJson(
                complex,
                ComplexResponse::class.java,
                this
            )
        }
        os
    }
    val moshiTime = measurePerf(threadBean) {
        val buf = Buffer()
        moshiAdapter.toJson(buf, complex)
        buf
    }
    val kserTime = measurePerf(threadBean) {
        val buf = Buffer()
        kJson.encodeToBufferedSink(complex, buf)
        buf
    }
    val jacksonTime = measurePerf(threadBean) {
        val os = ByteArrayOutputStream()
        jackson.writeValue(os, complex)
        os
    }
    val ghostTime = measurePerf(threadBean) {
        val buf = Buffer()
        Ghost.serialize(buf, complex)
        buf
    }

    return BenchmarkMetrics(
        gson = BenchResult(gsonTime.second, gsonTime.third),
        moshi = BenchResult(moshiTime.second, moshiTime.third),
        kser = BenchResult(kserTime.second, kserTime.third),
        jackson = BenchResult(jacksonTime.second, jacksonTime.third),
        ghost = BenchResult(ghostTime.second, ghostTime.third)
    )
}

// ============================================================================
// Stress & Failure Testing
// ============================================================================

@Suppress("CheckResult")
private fun runStressTests(
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    jackson: ObjectMapper
): StressMetrics {
    val tree = createTree(20)
    val treeBytes = generateNeutralJson(tree).encodeUtf8()

    val gsonTree = measureTimeNanos {
        gson.fromJson<Category>(
            GsonReader(InputStreamReader(ByteArrayInputStream(treeBytes.toByteArray()))),
            Category::class.java
        )
    }
    val moshiTree = measureTimeNanos {
        moshi.adapter<Category>().fromJson(Buffer().write(treeBytes.toByteArray()))
    }
    val kSerTree = measureTimeNanos { kJson.decodeFromString<Category>(treeBytes.utf8()) }
    val jacksonTree = measureTimeNanos { jackson.readValue<Category>(treeBytes.utf8()) }
    val ghostTree = measureTimeNanos { Ghost.deserialize<Category>(treeBytes.toByteArray()) }

    return StressMetrics(
        nesting = BenchmarkMetrics(
            gson = BenchResult(gsonTree, 0),
            moshi = BenchResult(moshiTree, 0),
            kser = BenchResult(kSerTree, 0),
            jackson = BenchResult(jacksonTree, 0),
            ghost = BenchResult(ghostTree, 0)
        ),
        large = BenchmarkMetrics(
            gson = BenchResult(0, 0),
            moshi = BenchResult(0, 0),
            kser = BenchResult(0, 0),
            jackson = BenchResult(0, 0),
            ghost = BenchResult(0, 0)
        )
    )
}

@Suppress("CheckResult")
private fun runFailureTests(data: ByteString): BenchmarkMetrics {
    val malformed = data.utf8().substring(0, data.size / 2)
    val bytes = malformed.encodeUtf8()

    val gson = Gson()
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val kser = Json { ignoreUnknownKeys = true }
    val jackson = ObjectMapper().registerModule(KotlinModule.Builder().build())

    val gsonTime = measureAvgFailSpeed {
        try {
            gson.fromJson(malformed, ComplexResponse::class.java)
        } catch (_: Exception) {
        }
    }
    val moshiTime = measureAvgFailSpeed {
        try {
            moshi.adapter<ComplexResponse>().fromJson(malformed)
        } catch (_: Exception) {
        }
    }
    val kserTime = measureAvgFailSpeed {
        try {
            kser.decodeFromString<ComplexResponse>(malformed)
        } catch (_: Exception) {
        }
    }
    val jacksonTime = measureAvgFailSpeed {
        try {
            jackson.readValue<ComplexResponse>(malformed)
        } catch (_: Exception) {
        }
    }
    val ghostTime = measureAvgFailSpeed {
        try {
            Ghost.deserialize<ComplexResponse>(bytes.toByteArray())
        } catch (_: Exception) {
        }
    }

    return BenchmarkMetrics(
        gson = BenchResult(gsonTime, 0),
        moshi = BenchResult(moshiTime, 0),
        kser = BenchResult(kserTime, 0),
        jackson = BenchResult(jacksonTime, 0),
        ghost = BenchResult(ghostTime, 0)
    )
}

// ============================================================================
// Statistical Math & Averages
// ============================================================================

private fun calculateFinalResults(sessions: List<BenchmarkSessionResults>): BenchmarkSessionResults {
    if (sessions.size == 1) return sessions.last()

    return BenchmarkSessionResults(
        listMedium = averageModeMetrics(sessions.map { it.listMedium }),
        syncLarge = averageModeMetrics(sessions.map { it.syncLarge }),
        writing = averageModeMetrics(sessions.map { it.writing }),
        stress = StressMetrics(
            nesting = averageMetrics(sessions.map { it.stress.nesting }),
            large = averageMetrics(sessions.map { it.stress.large })
        ),
        failure = averageMetrics(sessions.map { it.failure })
    )
}

private fun averageModeMetrics(list: List<ModeMetrics>): ModeMetrics {
    return ModeMetrics(
        string = averageMetrics(list.map { it.string }),
        bytes = averageMetrics(list.map { it.bytes }),
        streaming = averageMetrics(list.map { it.streaming })
    )
}

private fun averageMetrics(list: List<BenchmarkMetrics>): BenchmarkMetrics {
    return BenchmarkMetrics(
        gson = averageBenchResult(list.map { it.gson }),
        moshi = averageBenchResult(list.map { it.moshi }),
        kser = averageBenchResult(list.map { it.kser }),
        jackson = averageBenchResult(list.map { it.jackson }),
        ghost = averageBenchResult(list.map { it.ghost })
    )
}

private fun averageBenchResult(list: List<BenchResult>): BenchResult {
    val avgNanos = list.map { it.nanos }.average().toLong()
    val avgBytes = list.map { it.allocBytes }.average().toLong()

    val stDevNanos = if (list.size > 1) {
        val avg = avgNanos / 1_000_000.0
        val variance = list.map { (it.nanos / 1_000_000.0 - avg).let { d -> d * d } }.average()
        (sqrt(variance) * 1_000_000.0).toLong()
    } else 0L

    return BenchResult(avgNanos, avgBytes, stDevNanos)
}

// ============================================================================
// Printing & Presentation
// ============================================================================

private fun printFinalResults(finalResults: BenchmarkSessionResults, runs: Int) {
    val titleSuffix = if (runs > 1) " (STATISTICAL AVG OF $runs RUNS)" else ""

    printModeTables(
        "DESERIALIZATION: LIST_MEDIUM (200 objects)$titleSuffix",
        finalResults.listMedium
    )
    printModeTables(
        "DESERIALIZATION: SYNC_FULL_LARGE (2000 objects)$titleSuffix",
        finalResults.syncLarge
    )
    printModeTables("SERIALIZATION: WRITING (1000 objects)$titleSuffix", finalResults.writing)
    printRankedTable(
        "STRESS TEST: DEEP NESTING (20 Levels)$titleSuffix",
        finalResults.stress.nesting
    )
    printRankedTable("FAILURE RESILIENCE (Malformed JSON)$titleSuffix", finalResults.failure)
}

private fun printModeTables(title: String, metrics: ModeMetrics) {
    println("\n========================================================")
    println("BENCHMARK: $title")
    println("========================================================")
    printRankedSubTable("STRING MODE", metrics.string)
    printRankedSubTable("BYTES MODE", metrics.bytes)
    printRankedSubTable("STREAMING MODE", metrics.streaming)
}

private fun printRankedSubTable(label: String, metrics: BenchmarkMetrics) {
    println("\n--- $label ---")
    printRankedTableBody(metrics)
}

private fun printRankedTable(title: String, metrics: BenchmarkMetrics) {
    println("\n========================================================")
    println("BENCHMARK: $title")
    println("========================================================")
    printRankedTableBody(metrics)
}

private data class EngineRank(
    val name: String,
    val nanos: Long,
    val mem: Long,
    val stDevNanos: Long
)

private fun printRankedTableBody(metrics: BenchmarkMetrics) {
    val rankings = listOf(
        EngineRank(
            "GHOST",
            metrics.ghost.nanos,
            metrics.ghost.allocBytes,
            metrics.ghost.stdevNanos
        ),
        EngineRank(
            "JACKSON",
            metrics.jackson.nanos,
            metrics.jackson.allocBytes,
            metrics.jackson.stdevNanos
        ),
        EngineRank(
            "MOSHI",
            metrics.moshi.nanos,
            metrics.moshi.allocBytes,
            metrics.moshi.stdevNanos
        ),
        EngineRank("KSER", metrics.kser.nanos, metrics.kser.allocBytes, metrics.kser.stdevNanos),
        EngineRank("GSON", metrics.gson.nanos, metrics.gson.allocBytes, metrics.gson.stdevNanos)
    ).sortedBy { if (it.nanos <= 0) Long.MAX_VALUE else it.nanos }

    println("| RANK | ENGINE   | TOTAL(ms)       | MEM(KB)    |")
    println("|------|----------|-----------------|------------|")

    rankings.forEachIndexed { index, rank ->
        val totalMs = rank.nanos / 1_000_000.0
        val stDevMs = rank.stDevNanos / 1_000_000.0
        val memKb = rank.mem / 1024.0

        val timeStr = if (stDevMs > 0) {
            "%7.3f ±%-5.3f".format(totalMs, stDevMs)
        } else {
            "%7.3f        ".format(totalMs)
        }

        println("| %-4d | %-8s | %-15s | %10.1f |".format(index + 1, rank.name, timeStr, memKb))
    }

    val winner = rankings.first()
    val slowest = rankings.last()
    if (winner.nanos > 0 && slowest.nanos > 0) {
        val speedVsSlowest = ((slowest.nanos.toDouble() / winner.nanos.toDouble()) - 1.0) * 100.0
        println(
            "   WINNER: ${winner.name} (%.1f%% faster than ${slowest.name})".format(
                speedVsSlowest
            )
        )
    }
}

// ============================================================================
// Internal Utilities & Generation
// ============================================================================

private inline fun <reified T : Any> generateNeutralJson(data: T): String {
    return Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter<T>().toJson(data)
}

private fun initializePlatformDiagnostics(): ThreadMXBean? {
    val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    if (!threadBean.isThreadAllocatedMemorySupported) {
        println("Memory tracking not supported.")
        return null
    }
    threadBean.isThreadAllocatedMemoryEnabled = true
    return threadBean
}

private fun generateComplexData(count: Int): ComplexResponse {
    val history = IntArray(1000) { it }
    val meta = ExtremeMetadata(
        System.currentTimeMillis(),
        UserRole.EDITOR,
        listOf("beta"),
        1.2e-4,
        history
    )
    val users = List(count) { i ->
        BenchUser(
            i,
            "User $i",
            "u@e.com",
            1.0,
            true,
            UserRole.VIEWER,
            null
        )
    }
    return ComplexResponse(
        "success",
        users,
        meta,
        "42"
    )
}

private inline fun measureAvgFailSpeed(block: () -> Unit): Long {
    val startTime = System.nanoTime()
    repeat(100) { block() }
    return (System.nanoTime() - startTime) / 100
}

private fun createTree(d: Int): Category = if (d <= 0) {
    Category(name = "L")
} else {
    Category(
        name = "N",
        subCategories = listOf(createTree(d - 1))
    )
}

@Volatile
var blackHoleSink: Any? = null
fun consume(obj: Any?) {
    blackHoleSink = obj
}

private inline fun measureTimeNanos(block: () -> Unit): Long {
    val startTimeNanos = System.nanoTime()
    block()
    return System.nanoTime() - startTimeNanos
}

private inline fun <T> measurePerf(
    threadBean: ThreadMXBean,
    block: () -> T
): Triple<T, Long, Long> {
    val currentThreadId = Thread.currentThread().id
    val startAllocatedBytes = threadBean.getThreadAllocatedBytes(currentThreadId)
    val startTimeNanos = System.nanoTime()

    val result = block()

    val endTimeNanos = System.nanoTime()
    val endAllocatedBytes = threadBean.getThreadAllocatedBytes(currentThreadId)

    consume(result)
    val durationNanos = endTimeNanos - startTimeNanos
    val allocatedBytes = endAllocatedBytes - startAllocatedBytes

    return Triple(result, durationNanos, allocatedBytes)
}
