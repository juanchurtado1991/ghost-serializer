@file:OptIn(
    ExperimentalStdlibApi::class, InternalGhostApi::class,
    ExperimentalSerializationApi::class
)
@file:Suppress("SameParameterValue", "UNCHECKED_CAST")

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

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.integration.model.ExternalColor
import com.ghost.serialization.integration.model.ExternalColorSerializer
import com.ghost.serialization.integration.model.ExternalDate
import com.ghost.serialization.integration.model.ExternalDateSerializer
import kotlin.reflect.KClass

fun main(args: Array<String>) {
    val config = BenchmarkConfig.fromArgs(args)

    println("\n--- INITIALIZING PERFORMANCE BENCHMARKS (Runs: ${config.runs}) ---")
    
    val manualRegistry = object : GhostRegistry {
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
            return when (clazz) {
                ExternalColor::class -> ExternalColorSerializer as GhostSerializer<T>
                ExternalDate::class -> ExternalDateSerializer as GhostSerializer<T>
                else -> null
            }
        }

        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
            return mapOf(
                ExternalColor::class to ExternalColorSerializer,
                ExternalDate::class to ExternalDateSerializer
            )
        }
    }
    Ghost.addRegistry(manualRegistry)

    Ghost.prewarm()
    val threadBean = initializePlatformDiagnostics() ?: return

    val engines = BenchmarkEngines()

    val smallComplex = generateComplexData(20)
    val smallBytes = generateNeutralJson(smallComplex).encodeUtf8()

    val listMediumComplex = generateComplexData(200)
    val listMediumBytes = generateNeutralJson(listMediumComplex).encodeUtf8()

    val syncLargeComplex = generateComplexData(2000)
    val syncLargeBytes = generateNeutralJson(syncLargeComplex).encodeUtf8()

    val writingComplex = generateComplexData(1000)

    val stressTree = createTree(20)
    val stressTreeBytes = generateNeutralJson(stressTree).encodeUtf8()

    val failureMalformed = smallBytes.utf8().substring(0, smallBytes.size / 2)
    val failureBytes = failureMalformed.encodeUtf8()

    if (config.twitterOnly) {
        TwitterBenchmark.run(config.runs, config.warmupIters, threadBean)
        GhostYamlBenchmark.runTwitter(config.runs, config.warmupIters, threadBean)
        println("\n[COMPLETE] Benchmark execution finished.")
        exitProcess(0)
    }

    // 1. Cold Start
    runAndPrintColdStart(smallBytes, engines)

    // 2. Warmup
    performGc()
    runWarmupPhase(engines, smallBytes, smallComplex, config.warmupIters)

    // 3. Benchmarking Sessions
    performGc()
    val sessions = runBenchmarkSessions(
        config.runs,
        threadBean,
        engines,
        listMediumBytes = listMediumBytes,
        syncLargeBytes = syncLargeBytes,
        writingComplex = writingComplex,
        stressTreeBytes = stressTreeBytes,
        failureMalformed = failureMalformed,
        failureBytes = failureBytes
    )

    // 4. Calculate Final Results
    val finalResults = calculateFinalResults(sessions)

    // 5. Print Final Results
    printFinalResults(finalResults, config.runs)

    // 6. Ghost Special Features (exclusive capabilities, no competition)
    performGc()
    GhostSpecialFeaturesBenchmark.run(config.runs, config.warmupIters)

    // 7. Verification Tests
    if (!config.noTests) {
        val testResults = ParsingTestBenchmark.runAllTests()
        ParsingTestBenchmark.printUnifiedSummaryTable(testResults)
    }

    // 8. Twitter Macro Benchmark
    performGc()
    TwitterBenchmark.run(config.runs, config.warmupIters, threadBean)

    // 9. YAML Benchmarks
    performGc()
    GhostYamlBenchmark.run(config.runs, config.warmupIters, threadBean)

    // 10. YAML Twitter Macro Benchmark
    performGc()
    GhostYamlBenchmark.runTwitter(config.runs, config.warmupIters, threadBean)

    println("\n[COMPLETE] Benchmark execution finished.")
    exitProcess(0)
}



// ============================================================================
// Phase Executors
// ============================================================================

private fun runAndPrintColdStart(smallBytes: ByteString, engines: BenchmarkEngines) {
    val coldMetrics = runColdStart(smallBytes, engines.jackson)
    printRankedTable("COLD START (first parse, before JUnit suite)", coldMetrics)
}

@Suppress("CheckResult")
private fun runWarmupPhase(
    engines: BenchmarkEngines,
    smallBytes: ByteString,
    smallComplex: ComplexResponse,
    warmupIters: Int
) {
    println("\n🔥 Warming up JIT ($warmupIters iterations × 3 modes)...")
    val jsonString = smallBytes.utf8()
    val rawBytes = smallBytes.toByteArray()
    val stringFromBytes = String(rawBytes, Charsets.UTF_8)

    repeat(warmupIters) {
        // String mode
        engines.gson.fromJson(jsonString, ComplexResponse::class.java)
        engines.moshiAdapter.fromJson(jsonString)
        engines.kJson.decodeFromString<ComplexResponse>(jsonString)
        engines.jackson.readValue<ComplexResponse>(jsonString)
        Ghost.deserialize<ComplexResponse>(jsonString)
        engines.gson.toJson(smallComplex)
        engines.moshiAdapter.toJson(smallComplex)
        engines.kJson.encodeToString(smallComplex)
        engines.jackson.writeValueAsString(smallComplex)
        Ghost.encodeToString(smallComplex)

        // Bytes mode
        engines.gson.fromJson(stringFromBytes, ComplexResponse::class.java)
        engines.moshiAdapter.fromJson(stringFromBytes)
        engines.kJson.decodeFromString<ComplexResponse>(stringFromBytes)
        engines.jackson.readValue<ComplexResponse>(rawBytes)
        Ghost.deserialize<ComplexResponse>(rawBytes)
        engines.gson.toJson(smallComplex).toByteArray()
        engines.moshiAdapter.toJson(smallComplex).toByteArray()
        engines.kJson.encodeToString(smallComplex).toByteArray()
        engines.jackson.writeValueAsBytes(smallComplex)
        Ghost.encodeToBytes(smallComplex)

        // Streaming mode
        engines.gson.fromJson<ComplexResponse>(
            GsonReader(InputStreamReader(ByteArrayInputStream(rawBytes))),
            ComplexResponse::class.java
        )
        engines.moshiAdapter.fromJson(Buffer().write(rawBytes))
        engines.kJson.decodeFromBufferedSource<ComplexResponse>(Buffer().write(rawBytes))
        engines.jackson.readValue<ComplexResponse>(ByteArrayInputStream(rawBytes))
        Ghost.deserialize<ComplexResponse>(Buffer().write(rawBytes))
        ByteArrayOutputStream().also { os ->
            GsonWriter(OutputStreamWriter(os)).use { w ->
                engines.gson.toJson(smallComplex, ComplexResponse::class.java, w)
            }
        }
        Buffer().also { engines.moshiAdapter.toJson(it, smallComplex) }
        Buffer().also { engines.kJson.encodeToBufferedSink(smallComplex, it) }
        ByteArrayOutputStream().also { engines.jackson.writeValue(it, smallComplex) }
        Buffer().also { Ghost.serialize(it, smallComplex) }
    }
}

private fun runBenchmarkSessions(
    runs: Int,
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    listMediumBytes: ByteString,
    syncLargeBytes: ByteString,
    writingComplex: ComplexResponse,
    stressTreeBytes: ByteString,
    failureMalformed: String,
    failureBytes: ByteString
): List<BenchmarkSessionResults> {
    val sessions = mutableListOf<BenchmarkSessionResults>()

    repeat(runs) { i ->
        if (runs > 1 && (i + 1) % 5000 == 0) {
            println("[Run ${i + 1}/$runs] Benchmarking...")
        }

        val listMediumMetrics = runListMediumDeserialization(threadBean, engines, listMediumBytes)
        val syncLargeMetrics = runSyncLargeDeserialization(threadBean, engines, syncLargeBytes)
        val writingMetrics = runSerialization(threadBean, engines, writingComplex)
        val stressMetrics =
            runStressTests(engines.gson, engines.moshi, engines.kJson, engines.jackson, stressTreeBytes)
        val failureMetrics = runFailureTests(engines.gson, engines.moshi, engines.kJson, engines.jackson, failureMalformed, failureBytes)

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
    engines: BenchmarkEngines,
    bytes: ByteString
): ModeMetrics {
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
    engines: BenchmarkEngines,
    bytes: ByteString
): ModeMetrics {
    return runDeserializationAllModes(
        threadBean,
        engines.gson,
        engines.moshi,
        engines.kJson,
        engines.jackson,
        bytes
    )
}

private fun runSerialization(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    complex: ComplexResponse
): ModeMetrics {
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
// Measurement Factory
// ============================================================================

private fun buildMetrics(
    gson: Triple<*, Long, Long>,
    moshi: Triple<*, Long, Long>,
    kser: Triple<*, Long, Long>,
    jackson: Triple<*, Long, Long>,
    ghost: Triple<*, Long, Long>
): BenchmarkMetrics = BenchmarkMetrics(
    gson = BenchResult(gson.second, gson.third),
    moshi = BenchResult(moshi.second, moshi.third),
    kser = BenchResult(kser.second, kser.third),
    jackson = BenchResult(jackson.second, jackson.third),
    ghost = BenchResult(ghost.second, ghost.third)
)

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

    return buildMetrics(gsonTime, moshiTime, kserTime, jacksonTime, ghostTime)
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

    return buildMetrics(gsonTime, moshiTime, kserTime, jacksonTime, ghostTime)
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

    return buildMetrics(gsonTime, moshiTime, kserTime, jacksonTime, ghostTime)
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

    return buildMetrics(gsonTime, moshiTime, kserTime, jacksonTime, ghostTime)
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

    return buildMetrics(gsonTime, moshiTime, kserTime, jacksonTime, ghostTime)
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
    jackson: ObjectMapper,
    treeBytes: ByteString
): StressMetrics {
    val treeString = treeBytes.utf8()
    val treeRawBytes = treeBytes.toByteArray()

    val gsonTree = measureTimeNanos {
        gson.fromJson<Category>(
            GsonReader(InputStreamReader(ByteArrayInputStream(treeRawBytes))),
            Category::class.java
        )
    }
    val moshiTree = measureTimeNanos {
        moshi.adapter<Category>().fromJson(Buffer().write(treeRawBytes))
    }
    val kSerTree = measureTimeNanos { kJson.decodeFromString<Category>(treeString) }
    val jacksonTree = measureTimeNanos { jackson.readValue<Category>(treeString) }
    val ghostTree = measureTimeNanos { Ghost.deserialize<Category>(treeRawBytes) }

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
private fun runFailureTests(
    gson: Gson,
    moshi: Moshi,
    kser: Json,
    jackson: ObjectMapper,
    malformed: String,
    bytes: ByteString
): BenchmarkMetrics {
    val rawBytes = bytes.toByteArray()

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
            Ghost.deserialize<ComplexResponse>(rawBytes)
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
        val memSavedVsSlowest = if (slowest.mem > 0) {
            ((slowest.mem.toDouble() - winner.mem.toDouble()) / slowest.mem.toDouble()) * 100.0
        } else {
            0.0
        }
        val memString = if (memSavedVsSlowest >= 0.0) {
            "%.1f%% less memory".format(memSavedVsSlowest)
        } else {
            "but uses %.1f%% MORE memory".format(-memSavedVsSlowest)
        }
        println(
            "   WINNER: ${winner.name} (%.1f%% faster than ${slowest.name}, %s)".format(
                speedVsSlowest,
                memString
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

private fun performGc() {
    System.gc()
    System.runFinalization()
    Thread.sleep(100)
    System.gc()
}

