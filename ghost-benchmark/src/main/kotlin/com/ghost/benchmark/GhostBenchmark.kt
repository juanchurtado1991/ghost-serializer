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
import com.google.gson.stream.JsonReader as GsonReader
import com.google.gson.stream.JsonWriter as GsonWriter

// Synthetic harness, tables, and shared helpers — entry points live in [BenchmarkLauncher].

// ============================================================================
// Phase Executors
// ============================================================================

internal fun runAndPrintColdStart(smallBytes: ByteString, engines: BenchmarkEngines) {
    val coldMetrics = runColdStart(smallBytes, engines.jackson)
    printRankedTable("COLD START (first parse, before JUnit suite)", coldMetrics)
}

@Suppress("CheckResult")
internal fun runWarmupPhase(
    engines: BenchmarkEngines,
    smallBytes: ByteString,
    smallComplex: ComplexResponse
) {
    val jsonString = smallBytes.utf8()
    val rawBytes = smallBytes.toByteArray()
    val stringFromBytes = String(rawBytes, Charsets.UTF_8)

    BenchmarkProgress.logStep("ComplexResponse (string / bytes / streaming × all engines)")
    BenchmarkProgress.repeatWithProgress("Global ComplexResponse", BenchmarkStandard.WARMUP_ITERATIONS) {
        // String mode
        engines.gson.fromJson(jsonString, ComplexResponse::class.java)
        engines.kJson.decodeFromString<ComplexResponse>(jsonString)
        engines.jackson.readValue<ComplexResponse>(jsonString)
        Ghost.deserialize<ComplexResponse>(jsonString)
        engines.gson.toJson(smallComplex)
        engines.kJson.encodeToString(smallComplex)
        engines.jackson.writeValueAsString(smallComplex)
        Ghost.encodeToString(smallComplex)

        // Bytes mode
        engines.gson.fromJson(stringFromBytes, ComplexResponse::class.java)
        engines.kJson.decodeFromString<ComplexResponse>(stringFromBytes)
        engines.jackson.readValue<ComplexResponse>(rawBytes)
        Ghost.deserialize<ComplexResponse>(rawBytes)
        engines.gson.toJson(smallComplex).toByteArray()
        engines.kJson.encodeToString(smallComplex).toByteArray()
        engines.jackson.writeValueAsBytes(smallComplex)
        Ghost.encodeToBytes(smallComplex)

        // Streaming mode
        engines.gson.fromJson<ComplexResponse>(
            GsonReader(InputStreamReader(ByteArrayInputStream(rawBytes))),
            ComplexResponse::class.java
        )
        engines.kJson.decodeFromBufferedSource<ComplexResponse>(Buffer().write(rawBytes))
        engines.jackson.readValue<ComplexResponse>(ByteArrayInputStream(rawBytes))
        Ghost.deserialize<ComplexResponse>(Buffer().write(rawBytes))
        ByteArrayOutputStream().also { os ->
            GsonWriter(OutputStreamWriter(os)).use { w ->
                engines.gson.toJson(smallComplex, ComplexResponse::class.java, w)
            }
        }
        Buffer().also { engines.kJson.encodeToBufferedSink(smallComplex, it) }
        ByteArrayOutputStream().also { engines.jackson.writeValue(it, smallComplex) }
        Buffer().also { Ghost.serialize(it, smallComplex) }
    }
}

internal fun runSyntheticBenchmarks(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    payloads: BenchmarkPayloads,
): SyntheticRunResults {
    val listSessions = runDeserializationSuite(
        "LIST_MEDIUM",
        threadBean,
        engines.gson,
        engines.kJson,
        engines.jackson,
        payloads.listMediumBytes,
    )

    performPhaseGc()

    val syncSessions = runDeserializationSuite(
        "SYNC_FULL_LARGE",
        threadBean,
        engines.gson,
        engines.kJson,
        engines.jackson,
        payloads.syncLargeBytes,
    )

    performPhaseGc()

    val writingSessions = runSerializationSuite(
        "WRITING",
        threadBean,
        engines.gson,
        engines.kJson,
        engines.jackson,
        payloads.writingComplex,
    )

    val stressMetrics = runStressTests(engines.gson, engines.kJson, engines.jackson, payloads.stressTreeBytes)
    val failureMetrics = runFailureTests(
        engines.gson,
        engines.kJson,
        engines.jackson,
        payloads.failureMalformed,
        payloads.failureBytes,
    )

    return SyntheticRunResults(
        aggregated = BenchmarkSessionResults(
            listMedium = averageModeMetrics(listSessions),
            syncLarge = averageModeMetrics(syncSessions),
            writing = averageModeMetrics(writingSessions),
            stress = stressMetrics,
            failure = failureMetrics,
        ),
        listSessions = listSessions,
        syncSessions = syncSessions,
        writingSessions = writingSessions,
    )
}

private fun runDeserializationSuite(
    suiteLabel: String,
    threadBean: ThreadMXBean,
    gson: Gson,
    kJson: Json,
    jackson: ObjectMapper,
    data: ByteString,
): List<ModeMetrics> {
    val rawBytes = data.toByteArray()
    val jsonString = data.utf8()
    val decodeSinks = StreamingDecodeSinks(rawBytes)

    return runModeMetricsSessions(suiteLabel) { sessionIndex ->
        ModeMetrics(
            string = measureStringDeserialization(threadBean, gson, kJson, jackson, jsonString, sessionIndex),
            bytes = measureBytesDeserialization(threadBean, gson, kJson, jackson, rawBytes, sessionIndex),
            streaming = measureStreamingDeserialization(
                threadBean,
                gson,
                kJson,
                jackson,
                decodeSinks,
                sessionIndex,
            ),
        )
    }
}

private fun runSerializationSuite(
    suiteLabel: String,
    threadBean: ThreadMXBean,
    gson: Gson,
    kJson: Json,
    jackson: ObjectMapper,
    complex: ComplexResponse,
): List<ModeMetrics> {
    return runModeMetricsSessions(suiteLabel) { sessionIndex ->
        ModeMetrics(
            string = measureStringSerialization(threadBean, gson, kJson, jackson, complex, sessionIndex),
            bytes = measureBytesSerialization(threadBean, gson, kJson, jackson, complex, sessionIndex),
            streaming = measureStreamingSerialization(threadBean, gson, kJson, jackson, complex, sessionIndex),
        )
    }
}

private fun runModeMetricsSessions(
    label: String,
    block: (sessionIndex: Int) -> ModeMetrics,
): List<ModeMetrics> {
    val sessions = mutableListOf<ModeMetrics>()
    BenchmarkProgress.repeatWithProgress(label, BenchmarkStandard.SYNTHETIC_SESSIONS) { sessionIndex ->
        sessions.add(block(sessionIndex))
    }
    return sessions
}

/** GC between major benchmark phases only — never inside per-session hot loops. */
internal fun performPhaseGc() {
    System.gc()
    System.runFinalization()
}

// ============================================================================
// Measurement Factory
// ============================================================================

private fun measureEnginesRotated(
    sessionIndex: Int,
    threadBean: ThreadMXBean,
    engines: List<Pair<String, () -> Any?>>,
): BenchmarkMetrics {
    val byName = engines.associate { it.first to it.second }
    val ghostBlock = byName.getValue("ghost")
    val kserBlock = byName.getValue("kser")
    val thirdParty = engines.filter { (name, _) -> name != "ghost" && name != "kser" }

    // Regression signal first — Ghost vs KSER back-to-back, no GC between them.
    val ghostKserOrder = if (sessionIndex % 2 == 0) {
        listOf("ghost" to ghostBlock, "kser" to kserBlock)
    } else {
        listOf("kser" to kserBlock, "ghost" to ghostBlock)
    }
    val ghostKserResults = linkedMapOf<String, BenchResult>()
    for ((name, block) in ghostKserOrder) {
        val (result, nanos, alloc) = measurePerfBatched(
            threadBean,
            BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION,
            block,
        )
        consume(result)
        ghostKserResults[name] = BenchResult(nanos, alloc)
    }

    val thirdPartyResults = linkedMapOf<String, BenchResult>()
    val rotatedThirdParty = thirdParty.rotateLeft(sessionIndex % thirdParty.size.coerceAtLeast(1))
    for ((name, block) in rotatedThirdParty) {
        val (result, nanos, alloc) = measurePerfBatched(
            threadBean,
            BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION,
            block,
        )
        consume(result)
        thirdPartyResults[name] = BenchResult(nanos, alloc)
    }

    return BenchmarkMetrics(
        gson = thirdPartyResults.getValue("gson"),
        moshi = NOT_MEASURED,
        kser = ghostKserResults.getValue("kser"),
        jackson = thirdPartyResults.getValue("jackson"),
        ghost = ghostKserResults.getValue("ghost"),
    )
}

private fun <T> List<T>.rotateLeft(offset: Int): List<T> {
    if (isEmpty()) {
        return this
    }
    val shift = ((offset % size) + size) % size
    return drop(shift) + take(shift)
}

// ============================================================================
// Core Execution Logic
// ============================================================================

@Suppress("CheckResult")
private fun runColdStart(data: ByteString, jackson: ObjectMapper): BenchmarkMetrics {
    val coldGson = Gson()
    val coldKser = Json { ignoreUnknownKeys = true }

    val gsonTime = measureTimeNanos {
        coldGson.fromJson<ComplexResponse>(
            GsonReader(InputStreamReader(ByteArrayInputStream(data.toByteArray()))),
            ComplexResponse::class.java
        )
    }
    val kSerializationTime =
        measureTimeNanos { coldKser.decodeFromString<ComplexResponse>(data.utf8()) }
    val jacksonTime = measureTimeNanos { jackson.readValue<ComplexResponse>(data.utf8()) }
    val ghostTime = measureTimeNanos { Ghost.deserialize<ComplexResponse>(data.toByteArray()) }

    return BenchmarkMetrics(
        gson = BenchResult(gsonTime, 0),
        moshi = NOT_MEASURED,
        kser = BenchResult(kSerializationTime, 0),
        jackson = BenchResult(jacksonTime, 0),
        ghost = BenchResult(ghostTime, 0)
    )
}

private fun measureStreamingDeserialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    kJson: Json,
    jackson: ObjectMapper,
    sinks: StreamingDecodeSinks,
    sessionIndex: Int,
): BenchmarkMetrics {
    return measureEnginesRotated(sessionIndex, threadBean, listOf(
        "gson" to {
            gson.fromJson<ComplexResponse>(
                GsonReader(InputStreamReader(sinks.freshByteStream())),
                ComplexResponse::class.java,
            )
        },
        "kser" to { kJson.decodeFromBufferedSource<ComplexResponse>(sinks.freshOkioSource()) },
        "jackson" to { jackson.readValue<ComplexResponse>(sinks.freshByteStream()) },
        "ghost" to { Ghost.deserialize<ComplexResponse>(sinks.freshOkioSource()) },
    ))
}

// ============================================================================
// Measurement Helpers: Deserialization
// ============================================================================

private fun measureStringDeserialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    kJson: Json,
    jackson: ObjectMapper,
    jsonString: String,
    sessionIndex: Int,
): BenchmarkMetrics {
    return measureEnginesRotated(sessionIndex, threadBean, listOf(
        "gson" to { gson.fromJson(jsonString, ComplexResponse::class.java) },
        "kser" to { kJson.decodeFromString<ComplexResponse>(jsonString) },
        "jackson" to { jackson.readValue<ComplexResponse>(jsonString) },
        "ghost" to { Ghost.deserialize<ComplexResponse>(jsonString) },
    ))
}

private fun measureBytesDeserialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    kJson: Json,
    jackson: ObjectMapper,
    rawBytes: ByteArray,
    sessionIndex: Int,
): BenchmarkMetrics {
    val stringFromBytes = String(rawBytes, Charsets.UTF_8)
    return measureEnginesRotated(sessionIndex, threadBean, listOf(
        "gson" to { gson.fromJson(stringFromBytes, ComplexResponse::class.java) },
        "kser" to { kJson.decodeFromString<ComplexResponse>(stringFromBytes) },
        "jackson" to { jackson.readValue<ComplexResponse>(rawBytes) },
        "ghost" to { Ghost.deserialize<ComplexResponse>(rawBytes) },
    ))
}

// ============================================================================
// Measurement Helpers: Serialization
// ============================================================================

private fun measureStringSerialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    kJson: Json,
    jackson: ObjectMapper,
    complex: ComplexResponse,
    sessionIndex: Int,
): BenchmarkMetrics {
    return measureEnginesRotated(sessionIndex, threadBean, listOf(
        "gson" to { gson.toJson(complex) },
        "kser" to { kJson.encodeToString(complex) },
        "jackson" to { jackson.writeValueAsString(complex) },
        "ghost" to { Ghost.encodeToString(complex) },
    ))
}

private fun measureBytesSerialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    kJson: Json,
    jackson: ObjectMapper,
    complex: ComplexResponse,
    sessionIndex: Int,
): BenchmarkMetrics {
    return measureEnginesRotated(sessionIndex, threadBean, listOf(
        "gson" to { gson.toJson(complex).toByteArray() },
        "kser" to { kJson.encodeToString(complex).toByteArray() },
        "jackson" to { jackson.writeValueAsBytes(complex) },
        "ghost" to { Ghost.encodeToBytes(complex) },
    ))
}

private fun measureStreamingSerialization(
    threadBean: ThreadMXBean,
    gson: Gson,
    kJson: Json,
    jackson: ObjectMapper,
    complex: ComplexResponse,
    sessionIndex: Int,
): BenchmarkMetrics {
    return measureEnginesRotated(sessionIndex, threadBean, listOf(
        "gson" to {
            val os = StreamingEncodeSinks.gsonOutput()
            OutputStreamWriter(os).use { writer ->
                GsonWriter(writer).use { jsonWriter ->
                    gson.toJson(complex, ComplexResponse::class.java, jsonWriter)
                }
            }
            os
        },
        "kser" to {
            val buf = StreamingEncodeSinks.okioBuffer()
            kJson.encodeToBufferedSink(complex, buf)
            buf
        },
        "jackson" to {
            val os = StreamingEncodeSinks.jacksonOutput()
            jackson.writeValue(os, complex)
            os
        },
        "ghost" to {
            val buf = StreamingEncodeSinks.okioBuffer()
            Ghost.serialize(buf, complex)
            buf
        },
    ))
}

// ============================================================================
// Stress & Failure Testing
// ============================================================================

@Suppress("CheckResult")
private fun runStressTests(
    gson: Gson,
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
    val kSerTree = measureTimeNanos { kJson.decodeFromString<Category>(treeString) }
    val jacksonTree = measureTimeNanos { jackson.readValue<Category>(treeString) }
    val ghostTree = measureTimeNanos { Ghost.deserialize<Category>(treeRawBytes) }

    return StressMetrics(
        nesting = BenchmarkMetrics(
            gson = BenchResult(gsonTree, 0),
            moshi = NOT_MEASURED,
            kser = BenchResult(kSerTree, 0),
            jackson = BenchResult(jacksonTree, 0),
            ghost = BenchResult(ghostTree, 0)
        ),
        large = BenchmarkMetrics(
            gson = BenchResult(0, 0),
            moshi = NOT_MEASURED,
            kser = BenchResult(0, 0),
            jackson = BenchResult(0, 0),
            ghost = BenchResult(0, 0)
        )
    )
}

@Suppress("CheckResult")
private fun runFailureTests(
    gson: Gson,
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
        moshi = NOT_MEASURED,
        kser = BenchResult(kserTime, 0),
        jackson = BenchResult(jacksonTime, 0),
        ghost = BenchResult(ghostTime, 0)
    )
}

// ============================================================================
// Statistical Math & Averages
// ============================================================================

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
        moshi = NOT_MEASURED,
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

internal fun printFinalResults(finalResults: BenchmarkSessionResults) {
    val sessions = BenchmarkStandard.SYNTHETIC_SESSIONS
    val samples = BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION
    val titleSuffix = " (STATISTICAL AVG OF $sessions SESSIONS × $samples SAMPLES)"

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

/**
 * Maps per-session synthetic measurements into calculator observations.
 *
 * Speed uses the **median of per-session** Ghost-vs-KSER advantage ratios (robust to outlier
 * sessions). Ghost and KSER are measured back-to-back in each session (see [measureEnginesRotated]).
 * Encoded as ghost=1.0 and kser=median(kser_i/ghost_i) for [RegressionCalculator.Metric.LATENCY].
 */
internal fun syntheticObservations(run: SyntheticRunResults): List<RegressionCalculator.Observed> {
    fun row(
        group: String,
        mode: String,
        sessions: List<ModeMetrics>,
        selector: (ModeMetrics) -> BenchmarkMetrics,
    ): RegressionCalculator.Observed {
        val perSession = sessions.map(selector)
        val advantages = perSession.mapNotNull { metrics ->
            val ghostMs = metrics.ghost.nanos / 1_000_000.0
            val kserMs = metrics.kser.nanos / 1_000_000.0
            if (ghostMs <= 0.0) {
                null
            } else {
                kserMs / ghostMs
            }
        }
        val medianAdvantage = median(advantages)
        return RegressionCalculator.Observed(
            group = group,
            category = mode,
            metric = RegressionCalculator.Metric.LATENCY,
            ghostSpeed = 1.0,
            kserSpeed = medianAdvantage,
            ghostMemKb = perSession.map { it.ghost.allocBytes / 1024.0 }.average(),
            kserMemKb = perSession.map { it.kser.allocBytes / 1024.0 }.average(),
        )
    }
    return listOf(
        row(RegressionCalculator.LIST_MEDIUM, RegressionCalculator.MODE_STRING, run.listSessions) { it.string },
        row(RegressionCalculator.LIST_MEDIUM, RegressionCalculator.MODE_BYTES, run.listSessions) { it.bytes },
        row(RegressionCalculator.LIST_MEDIUM, RegressionCalculator.MODE_STREAMING, run.listSessions) { it.streaming },
        row(RegressionCalculator.SYNC_FULL, RegressionCalculator.MODE_STRING, run.syncSessions) { it.string },
        row(RegressionCalculator.SYNC_FULL, RegressionCalculator.MODE_BYTES, run.syncSessions) { it.bytes },
        row(RegressionCalculator.SYNC_FULL, RegressionCalculator.MODE_STREAMING, run.syncSessions) { it.streaming },
        row(RegressionCalculator.WRITING, RegressionCalculator.MODE_STRING, run.writingSessions) { it.string },
        row(RegressionCalculator.WRITING, RegressionCalculator.MODE_BYTES, run.writingSessions) { it.bytes },
        row(RegressionCalculator.WRITING, RegressionCalculator.MODE_STREAMING, run.writingSessions) { it.streaming },
    )
}

private fun median(values: List<Double>): Double {
    if (values.isEmpty()) {
        return 0.0
    }
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2.0
    } else {
        sorted[mid]
    }
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
        EngineRank("KSER", metrics.kser.nanos, metrics.kser.allocBytes, metrics.kser.stdevNanos),
        EngineRank("GSON", metrics.gson.nanos, metrics.gson.allocBytes, metrics.gson.stdevNanos)
    ).filter { it.nanos > 0 }
        .sortedBy { it.nanos }

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

internal fun generateNeutralJson(data: Any): String {
    return Gson().toJson(data)
}

internal fun initializePlatformDiagnostics(): ThreadMXBean? {
    val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    if (!threadBean.isThreadAllocatedMemorySupported) {
        println("Memory tracking not supported.")
        return null
    }
    threadBean.isThreadAllocatedMemoryEnabled = true
    return threadBean
}

internal fun generateComplexData(count: Int): ComplexResponse {
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

internal fun createTree(d: Int): Category = if (d <= 0) {
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

private inline fun <T> measurePerfBatched(
    threadBean: ThreadMXBean,
    samples: Int,
    crossinline block: () -> T,
): Triple<T, Long, Long> {
    val currentThreadId = Thread.currentThread().id
    val startAllocatedBytes = threadBean.getThreadAllocatedBytes(currentThreadId)
    val startTimeNanos = System.nanoTime()
    var lastResult: T? = null
    repeat(samples) {
        lastResult = block()
    }
    consume(lastResult)
    val endTimeNanos = System.nanoTime()
    val endAllocatedBytes = threadBean.getThreadAllocatedBytes(currentThreadId)
    val durationNanos = (endTimeNanos - startTimeNanos) / samples
    val allocatedBytes = (endAllocatedBytes - startAllocatedBytes) / samples
    @Suppress("UNCHECKED_CAST")
    return Triple(lastResult as T, durationNanos, allocatedBytes)
}

/** Reusable Okio / byte streams for streaming decode (payload fixed per suite). */
private class StreamingDecodeSinks(private val rawBytes: ByteArray) {

    private val okioBuffer = Buffer()

    fun freshByteStream(): ByteArrayInputStream = ByteArrayInputStream(rawBytes)

    fun freshOkioSource(): Buffer {
        okioBuffer.clear()
        okioBuffer.write(rawBytes)
        return okioBuffer
    }
}

/** Thread-local encode sinks so streaming serialization reuses buffers across batched samples. */
private object StreamingEncodeSinks {

    private val okioBuffer = ThreadLocal.withInitial { Buffer() }
    private val gsonOutput = ThreadLocal.withInitial { ByteArrayOutputStream(16_384) }
    private val jacksonOutput = ThreadLocal.withInitial { ByteArrayOutputStream(16_384) }

    fun okioBuffer(): Buffer = okioBuffer.get().also { it.clear() }

    fun gsonOutput(): ByteArrayOutputStream = gsonOutput.get().also { it.reset() }

    fun jacksonOutput(): ByteArrayOutputStream = jacksonOutput.get().also { it.reset() }
}

/** Placeholder when an engine is excluded from the benchmark harness (e.g. Moshi). */
private val NOT_MEASURED = BenchResult(0, 0)

