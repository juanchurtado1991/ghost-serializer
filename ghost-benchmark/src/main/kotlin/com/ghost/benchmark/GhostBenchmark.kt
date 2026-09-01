@file:OptIn(
    ExperimentalStdlibApi::class, InternalGhostApi::class,
    ExperimentalSerializationApi::class
)
@file:Suppress("SameParameterValue", "UNCHECKED_CAST")

package com.ghost.benchmark

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
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer
import okio.ByteString
import java.lang.management.ManagementFactory
import kotlin.math.sqrt

/**
 * Synthetic JSON harness: LIST / SYNC / WRITING workloads, cold-start timing, result tables,
 * and shared measurement helpers used by [BenchmarkSuite.SYNTHETIC] and [BenchmarkSuite.FULL].
 *
 * CLI orchestration lives in [main] (`BenchmarkLauncher.kt`).
 */

// ============================================================================
// Phase Executors
// ============================================================================

/** Runs and prints the one-shot cold-start parse table before global JIT warmup. */
internal fun runAndPrintColdStart(smallBytes: ByteString) {
    val coldMetrics = runColdStart(smallBytes)
    printColdStartTable("COLD START (first parse, before JUnit suite)", coldMetrics)
}

/** Global JIT warmup across string, bytes, and streaming channels for all three engines. */
@Suppress("CheckResult")
internal fun runWarmupPhase(
    engines: BenchmarkEngines,
    smallBytes: ByteString,
    smallComplex: ComplexResponse
) {
    val jsonString = smallBytes.utf8()
    val rawBytes = smallBytes.toByteArray()
    val stringFromBytes = String(rawBytes, Charsets.UTF_8)
    val moshiAdapter = engines.complexResponseAdapter

    BenchmarkProgress.logStep("ComplexResponse (string / bytes / streaming × all engines)")
    BenchmarkProgress.repeatWithProgress(
        "Global ComplexResponse",
        BenchmarkStandard.WARMUP_ITERATIONS
    ) {
        // String mode
        moshiAdapter.fromJson(jsonString)
        engines.kJson.decodeFromString<ComplexResponse>(jsonString)
        Ghost.deserialize<ComplexResponse>(jsonString)
        moshiAdapter.toJson(smallComplex)
        engines.kJson.encodeToString(smallComplex)
        Ghost.encodeToString(smallComplex)

        // Bytes mode
        moshiAdapter.fromJson(stringFromBytes)
        engines.kJson.decodeFromString<ComplexResponse>(stringFromBytes)
        Ghost.deserialize<ComplexResponse>(rawBytes)
        moshiAdapter.toJson(smallComplex).encodeToByteArray()
        engines.kJson.encodeToString(smallComplex).toByteArray()
        Ghost.encodeToBytes(smallComplex)

        // Streaming mode
        moshiAdapter.fromJson(JsonReader.of(Buffer().write(rawBytes)))
        engines.kJson.decodeFromBufferedSource<ComplexResponse>(Buffer().write(rawBytes))
        Ghost.deserialize<ComplexResponse>(Buffer().write(rawBytes))
        Buffer().also { buf ->
            JsonWriter.of(buf).use { writer ->
                moshiAdapter.toJson(writer, smallComplex)
            }
        }
        Buffer().also { engines.kJson.encodeToBufferedSink(smallComplex, it) }
        Buffer().also { Ghost.serialize(it, smallComplex) }
    }
}

/** Executes LIST, SYNC, WRITING, stress, and failure synthetic suites; returns raw session lists. */
internal fun runSyntheticBenchmarks(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    payloads: BenchmarkPayloads,
): SyntheticRunResults {
    val listSessions = runDeserializationSuite(
        "LIST_MEDIUM",
        threadBean,
        engines,
        payloads.listMediumBytes,
    )

    performPhaseGc()

    val syncSessions = runDeserializationSuite(
        "SYNC_FULL_LARGE",
        threadBean,
        engines,
        payloads.syncLargeBytes,
    )

    performPhaseGc()

    val writingSessions = runSerializationSuite(
        "WRITING",
        threadBean,
        engines,
        payloads.writingComplex,
    )

    val stressMetrics = runStressTests(engines, payloads.stressTreeBytes)
    val failureMetrics = runFailureTests(
        engines,
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
    engines: BenchmarkEngines,
    data: ByteString,
): List<ModeMetrics> {
    val rawBytes = data.toByteArray()
    val jsonString = data.utf8()
    val decodeSinks = StreamingDecodeSinks(rawBytes)

    return runModeMetricsSessions(suiteLabel) { sessionIndex ->
        ModeMetrics(
            string = measureStringDeserialization(threadBean, engines, jsonString, sessionIndex),
            bytes = measureBytesDeserialization(threadBean, engines, rawBytes, sessionIndex),
            streaming = measureStreamingDeserialization(
                threadBean,
                engines,
                decodeSinks,
                sessionIndex,
            ),
        )
    }
}

private fun runSerializationSuite(
    suiteLabel: String,
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    complex: ComplexResponse,
): List<ModeMetrics> {
    return runModeMetricsSessions(suiteLabel) { sessionIndex ->
        ModeMetrics(
            string = measureStringSerialization(threadBean, engines, complex, sessionIndex),
            bytes = measureBytesSerialization(threadBean, engines, complex, sessionIndex),
            streaming = measureStreamingSerialization(threadBean, engines, complex, sessionIndex),
        )
    }
}

private fun runModeMetricsSessions(
    label: String,
    block: (sessionIndex: Int) -> ModeMetrics,
): List<ModeMetrics> {
    val sessions = mutableListOf<ModeMetrics>()
    BenchmarkProgress.repeatWithProgress(
        label,
        BenchmarkStandard.SYNTHETIC_SESSIONS
    ) { sessionIndex ->
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
    val moshiBlock = byName.getValue("moshi")

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

    val (moshiResult, moshiNanos, moshiAlloc) = measurePerfBatched(
        threadBean,
        BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION,
        moshiBlock,
    )
    consume(moshiResult)

    return BenchmarkMetrics(
        ghost = ghostKserResults.getValue("ghost"),
        kser = ghostKserResults.getValue("kser"),
        moshi = BenchResult(moshiNanos, moshiAlloc),
    )
}

// ============================================================================
// Core Execution Logic
// ============================================================================

@Suppress("CheckResult")
private fun runColdStart(data: ByteString): BenchmarkMetrics {
    val coldKser = Json { ignoreUnknownKeys = true }
    val coldMoshi = createBenchmarkMoshi()
    val moshiAdapter = coldMoshi.adapter(ComplexResponse::class.java)

    val moshiTime = measureTimeNanos {
        moshiAdapter.fromJson(JsonReader.of(Buffer().write(data.toByteArray())))
    }
    val kSerializationTime =
        measureTimeNanos { coldKser.decodeFromString<ComplexResponse>(data.utf8()) }
    val ghostTime = measureTimeNanos { Ghost.deserialize<ComplexResponse>(data.toByteArray()) }

    return BenchmarkMetrics(
        ghost = BenchResult(ghostTime, 0),
        kser = BenchResult(kSerializationTime, 0),
        moshi = BenchResult(moshiTime, 0),
    )
}

private fun measureStreamingDeserialization(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    sinks: StreamingDecodeSinks,
    sessionIndex: Int,
): BenchmarkMetrics {
    val moshiAdapter = engines.complexResponseAdapter
    return measureEnginesRotated(
        sessionIndex, threadBean, listOf(
            "moshi" to {
                moshiAdapter.fromJson(JsonReader.of(sinks.freshOkioSource()))
            },
            "kser" to { engines.kJson.decodeFromBufferedSource<ComplexResponse>(sinks.freshOkioSource()) },
            "ghost" to { Ghost.deserialize<ComplexResponse>(sinks.freshOkioSource()) },
        )
    )
}

// ============================================================================
// Measurement Helpers: Deserialization
// ============================================================================

private fun measureStringDeserialization(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    jsonString: String,
    sessionIndex: Int,
): BenchmarkMetrics {
    val moshiAdapter = engines.complexResponseAdapter
    return measureEnginesRotated(
        sessionIndex, threadBean, listOf(
            "moshi" to { moshiAdapter.fromJson(jsonString) },
            "kser" to { engines.kJson.decodeFromString<ComplexResponse>(jsonString) },
            "ghost" to { Ghost.deserialize<ComplexResponse>(jsonString) },
        )
    )
}

private fun measureBytesDeserialization(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    rawBytes: ByteArray,
    sessionIndex: Int,
): BenchmarkMetrics {
    val stringFromBytes = String(rawBytes, Charsets.UTF_8)
    val moshiAdapter = engines.complexResponseAdapter
    return measureEnginesRotated(
        sessionIndex, threadBean, listOf(
            "moshi" to { moshiAdapter.fromJson(stringFromBytes) },
            "kser" to { engines.kJson.decodeFromString<ComplexResponse>(stringFromBytes) },
            "ghost" to { Ghost.deserialize<ComplexResponse>(rawBytes) },
        )
    )
}

// ============================================================================
// Measurement Helpers: Serialization
// ============================================================================

private fun measureStringSerialization(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    complex: ComplexResponse,
    sessionIndex: Int,
): BenchmarkMetrics {
    val moshiAdapter = engines.complexResponseAdapter
    return measureEnginesRotated(
        sessionIndex, threadBean, listOf(
            "moshi" to { moshiAdapter.toJson(complex) },
            "kser" to { engines.kJson.encodeToString(complex) },
            "ghost" to { Ghost.encodeToString(complex) },
        )
    )
}

private fun measureBytesSerialization(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    complex: ComplexResponse,
    sessionIndex: Int,
): BenchmarkMetrics {
    val moshiAdapter = engines.complexResponseAdapter
    return measureEnginesRotated(
        sessionIndex, threadBean, listOf(
            "moshi" to { moshiAdapter.toJson(complex).encodeToByteArray() },
            "kser" to { engines.kJson.encodeToString(complex).toByteArray() },
            "ghost" to { Ghost.encodeToBytes(complex) },
        )
    )
}

private fun measureStreamingSerialization(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    complex: ComplexResponse,
    sessionIndex: Int,
): BenchmarkMetrics {
    val moshiAdapter = engines.complexResponseAdapter
    return measureEnginesRotated(
        sessionIndex, threadBean, listOf(
            "moshi" to {
                val buf = StreamingEncodeSinks.okioBuffer()
                JsonWriter.of(buf).use { writer ->
                    moshiAdapter.toJson(writer, complex)
                }
                buf
            },
            "kser" to {
                val buf = StreamingEncodeSinks.okioBuffer()
                engines.kJson.encodeToBufferedSink(complex, buf)
                buf
            },
            "ghost" to {
                val buf = StreamingEncodeSinks.okioBuffer()
                Ghost.serialize(buf, complex)
                buf
            },
        )
    )
}

// ============================================================================
// Stress & Failure Testing
// ============================================================================

@Suppress("CheckResult")
private fun runStressTests(
    engines: BenchmarkEngines,
    treeBytes: ByteString
): StressMetrics {
    val treeString = treeBytes.utf8()
    val treeRawBytes = treeBytes.toByteArray()
    val categoryAdapter = engines.moshi.adapter(Category::class.java)

    val moshiTree = measureTimeNanos {
        categoryAdapter.fromJson(JsonReader.of(Buffer().write(treeRawBytes)))
    }
    val kSerTree = measureTimeNanos { engines.kJson.decodeFromString<Category>(treeString) }
    val ghostTree = measureTimeNanos { Ghost.deserialize<Category>(treeRawBytes) }

    return StressMetrics(
        nesting = BenchmarkMetrics(
            ghost = BenchResult(ghostTree, 0),
            kser = BenchResult(kSerTree, 0),
            moshi = BenchResult(moshiTree, 0),
        ),
        large = BenchmarkMetrics(
            ghost = BenchResult(0, 0),
            kser = BenchResult(0, 0),
            moshi = BenchResult(0, 0),
        )
    )
}

@Suppress("CheckResult")
private fun runFailureTests(
    engines: BenchmarkEngines,
    malformed: String,
    bytes: ByteString
): BenchmarkMetrics {
    val rawBytes = bytes.toByteArray()
    val moshiAdapter = engines.complexResponseAdapter

    val moshiTime = measureAvgFailSpeed {
        try {
            moshiAdapter.fromJson(malformed)
        } catch (_: Exception) {
        }
    }
    val kserTime = measureAvgFailSpeed {
        try {
            engines.kJson.decodeFromString<ComplexResponse>(malformed)
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
        ghost = BenchResult(ghostTime, 0),
        kser = BenchResult(kserTime, 0),
        moshi = BenchResult(moshiTime, 0),
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
        ghost = averageBenchResult(list.map { it.ghost }),
        kser = averageBenchResult(list.map { it.kser }),
        moshi = averageBenchResult(list.map { it.moshi }),
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

/** Prints aggregated synthetic tables (latency, GB/s, allocation) for every workload. */
internal fun printFinalResults(finalResults: BenchmarkSessionResults, payloads: BenchmarkPayloads) {
    val sessions = BenchmarkStandard.SYNTHETIC_SESSIONS
    val samples = BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION
    val titleSuffix = " (STATISTICAL AVG OF $sessions SESSIONS × $samples SAMPLES)"

    printModeTables(
        "DESERIALIZATION: LIST_MEDIUM (200 objects)$titleSuffix",
        finalResults.listMedium,
        payloadBytes = payloads.listMediumBytes.size.toLong(),
    )
    printModeTables(
        "DESERIALIZATION: SYNC_FULL_LARGE (2000 objects)$titleSuffix",
        finalResults.syncLarge,
        payloadBytes = payloads.syncLargeBytes.size.toLong(),
    )
    printModeTables(
        "SERIALIZATION: WRITING (1000 objects)$titleSuffix",
        finalResults.writing,
        payloadBytes = payloads.writingBytes.size.toLong(),
    )
    printMicroLatencyTable(
        title = "STRESS TEST: DEEP NESTING (20 Levels)",
        subtitle = "Single-shot parse per engine after synthetic suite (632 B payload)",
        metrics = finalResults.stress.nesting,
    )
    printMicroLatencyTable(
        title = "FAILURE RESILIENCE (Malformed JSON)",
        subtitle = "Average of 100 failed parses per engine (2 581 B payload)",
        metrics = finalResults.failure,
    )
}

private fun printModeTables(title: String, metrics: ModeMetrics, payloadBytes: Long) {
    println("\n========================================================")
    println("BENCHMARK: $title")
    println("========================================================")
    println(
        "  Payload: %d bytes → µs/op and decimal GB/s (payload / seconds / 10⁹)".format(payloadBytes)
    )
    printRankedSubTable("STRING MODE", metrics.string, payloadBytes)
    printRankedSubTable("BYTES MODE", metrics.bytes, payloadBytes)
    printRankedSubTable("STREAMING MODE", metrics.streaming, payloadBytes)
}

private fun printRankedSubTable(label: String, metrics: BenchmarkMetrics, payloadBytes: Long) {
    println("\n--- $label ---")
    printRankedTableBody(metrics, payloadBytes)
}

/**
 * Cold start is one-time init latency, not throughput — GB/s would obscure the startup
 * cost and make the result payload-size-dependent, so this stays in milliseconds.
 */
private fun printColdStartTable(title: String, metrics: BenchmarkMetrics) {
    println("\n========================================================")
    println("BENCHMARK: $title")
    println("========================================================")

    val rankings = engineRankings(metrics).sortedBy { it.nanos }
    println("| RANK | ENGINE   | Latency (ms) |")
    println("|------|----------|--------------|")
    rankings.forEachIndexed { index, rank ->
        println(
            "| %-4d | %-8s | %12.3f |".format(
                index + 1,
                rank.name,
                rank.nanos / 1_000_000.0,
            )
        )
    }

    val winner = rankings.first()
    val slowest = rankings.last()
    val latencyReduction =
        ((slowest.nanos.toDouble() - winner.nanos.toDouble()) / slowest.nanos.toDouble()) * 100.0
    println(
        "   WINNER: ${winner.name} (%.1f%% lower latency than ${slowest.name})".format(
            latencyReduction
        )
    )
}

/**
 * Deep-nesting / malformed-JSON micro-benchmarks measure latency only (same GB/s rationale
 * as [printColdStartTable]); allocation isn't measured here.
 */
private fun printMicroLatencyTable(
    title: String,
    subtitle: String,
    metrics: BenchmarkMetrics,
) {
    println("\n========================================================")
    println("BENCHMARK: $title")
    println("========================================================")
    println("  $subtitle → latency only (µs/op)")

    val rankings = engineRankings(metrics).sortedBy { it.nanos }
    println("| RANK | ENGINE   | Latency (µs/op) |")
    println("|------|----------|-----------------|")
    rankings.forEachIndexed { index, rank ->
        println(
            "| %-4d | %-8s | %15.1f |".format(
                index + 1,
                rank.name,
                BenchmarkThroughput.nanosToMicros(rank.nanos),
            )
        )
    }

    val winner = rankings.first()
    val slowest = rankings.last()
    if (winner.nanos > 0 && slowest.nanos > 0) {
        val latencyReduction =
            ((slowest.nanos.toDouble() - winner.nanos.toDouble()) / slowest.nanos.toDouble()) * 100.0
        println(
            "   WINNER: ${winner.name} (%.1f%% lower latency than ${slowest.name})".format(
                latencyReduction
            )
        )
    }
}

/**
 * Maps per-session synthetic measurements into calculator observations.
 *
 * Speed uses the median of per-session Ghost-vs-KSER ratios (robust to outliers); Ghost and
 * KSER are measured back-to-back per session (see [measureEnginesRotated]). Encoded as
 * ghost=1.0, kser=median(kser_i/ghost_i) for [RegressionCalculator.Metric.LATENCY].
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
        row(
            RegressionCalculator.LIST_MEDIUM,
            RegressionCalculator.MODE_STRING,
            run.listSessions
        ) { it.string },
        row(
            RegressionCalculator.LIST_MEDIUM,
            RegressionCalculator.MODE_BYTES,
            run.listSessions
        ) { it.bytes },
        row(
            RegressionCalculator.LIST_MEDIUM,
            RegressionCalculator.MODE_STREAMING,
            run.listSessions
        ) { it.streaming },
        row(
            RegressionCalculator.SYNC_FULL,
            RegressionCalculator.MODE_STRING,
            run.syncSessions
        ) { it.string },
        row(
            RegressionCalculator.SYNC_FULL,
            RegressionCalculator.MODE_BYTES,
            run.syncSessions
        ) { it.bytes },
        row(
            RegressionCalculator.SYNC_FULL,
            RegressionCalculator.MODE_STREAMING,
            run.syncSessions
        ) { it.streaming },
        row(
            RegressionCalculator.WRITING,
            RegressionCalculator.MODE_STRING,
            run.writingSessions
        ) { it.string },
        row(
            RegressionCalculator.WRITING,
            RegressionCalculator.MODE_BYTES,
            run.writingSessions
        ) { it.bytes },
        row(
            RegressionCalculator.WRITING,
            RegressionCalculator.MODE_STREAMING,
            run.writingSessions
        ) { it.streaming },
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

private fun engineRankings(metrics: BenchmarkMetrics): List<EngineRank> {
    return listOf(
        EngineRank(
            "GHOST",
            metrics.ghost.nanos,
            metrics.ghost.allocBytes,
            metrics.ghost.stdevNanos
        ),
        EngineRank("KSER", metrics.kser.nanos, metrics.kser.allocBytes, metrics.kser.stdevNanos),
        EngineRank(
            "MOSHI",
            metrics.moshi.nanos,
            metrics.moshi.allocBytes,
            metrics.moshi.stdevNanos
        ),
    ).filter { it.nanos > 0L }
}

private fun printRankedTableBody(metrics: BenchmarkMetrics, payloadBytes: Long) {
    val rankings = engineRankings(metrics)
        .sortedByDescending {
            BenchmarkThroughput.nanosToGbPerSec(it.nanos, payloadBytes)
        }

    println("| RANK | ENGINE   | Throughput (GB/s) | Latency (µs/op)   | Mem (KB/op) |")
    println("|------|----------|-------------------|-------------------|------------|")

    rankings.forEachIndexed { index, rank ->
        val meanUs = BenchmarkThroughput.nanosToMicros(rank.nanos)
        val stdevUs = BenchmarkThroughput.nanosStdevToMicros(rank.stDevNanos)
        val meanGb = BenchmarkThroughput.nanosToGbPerSec(rank.nanos, payloadBytes)
        val stdevGb = BenchmarkThroughput.nanosStdevToGbPerSec(
            rank.nanos,
            rank.stDevNanos,
            payloadBytes,
        )
        val memKb = rank.mem / 1024.0
        val latencyStr = BenchmarkThroughput.formatMicrosWithStdev(meanUs, stdevUs)
        val speedStr = BenchmarkThroughput.formatGbPerSecWithStdev(meanGb, stdevGb)

        println(
            "| %-4d | %-8s | %-17s | %-17s | %10.1f |".format(
                index + 1,
                rank.name,
                speedStr,
                latencyStr,
                memKb,
            )
        )
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

/** Encodes [data] with KotlinX Serialization for neutral cross-engine JSON fixtures. */
internal fun generateNeutralJson(data: Any): String {
    val json = Json { ignoreUnknownKeys = true }
    @Suppress("UNCHECKED_CAST")
    return when (data) {
        is ComplexResponse -> json.encodeToString(data)
        is Category -> json.encodeToString(data)
        else -> error("Unsupported benchmark payload type: ${data::class.simpleName}")
    }
}

/** Enables [ThreadMXBean] thread allocation tracking; returns `null` when unsupported. */
internal fun initializePlatformDiagnostics(): ThreadMXBean? {
    val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    if (!threadBean.isThreadAllocatedMemorySupported) {
        println("Memory tracking not supported.")
        return null
    }
    threadBean.isThreadAllocatedMemoryEnabled = true
    return threadBean
}

private const val METADATA_HISTORY_SIZE = 1_000
private const val FAILURE_MEASURE_SAMPLES = 100
private const val TREE_LEAF_NAME = "L"
private const val TREE_NODE_NAME = "N"
private const val RESPONSE_STATUS_SUCCESS = "success"
private const val RESPONSE_CODE = "42"
private const val SAMPLE_USER_EMAIL = "u@e.com"
private const val SAMPLE_USER_NAME_PREFIX = "User "
private const val SAMPLE_META_TAG = "beta"
private const val SAMPLE_META_SCORE = 1.2e-4
private const val SAMPLE_USER_SCORE = 1.0

/** Builds a synthetic ComplexResponse with [count] users and fixed metadata. */
internal fun generateComplexData(count: Int): ComplexResponse {
    val history = IntArray(METADATA_HISTORY_SIZE) { it }
    val meta = ExtremeMetadata(
        System.currentTimeMillis(),
        UserRole.EDITOR,
        listOf(SAMPLE_META_TAG),
        SAMPLE_META_SCORE,
        history
    )
    val users = List(count) { i ->
        BenchUser(
            i,
            "$SAMPLE_USER_NAME_PREFIX$i",
            SAMPLE_USER_EMAIL,
            SAMPLE_USER_SCORE,
            true,
            UserRole.VIEWER,
            null
        )
    }
    return ComplexResponse(
        RESPONSE_STATUS_SUCCESS,
        users,
        meta,
        RESPONSE_CODE
    )
}

private inline fun measureAvgFailSpeed(block: () -> Unit): Long {
    val startTime = System.nanoTime()
    repeat(FAILURE_MEASURE_SAMPLES) { block() }
    return (System.nanoTime() - startTime) / FAILURE_MEASURE_SAMPLES
}

/** Builds a Category tree [depth] levels deep for nesting stress tests. */
internal fun createTree(depth: Int): Category = if (depth <= 0) {
    Category(name = TREE_LEAF_NAME)
} else {
    Category(
        name = TREE_NODE_NAME,
        subCategories = listOf(createTree(depth - 1))
    )
}

@Volatile
var blackHoleSink: Any? = null

/** Prevents the JVM from dead-code-eliminating benchmark results. */
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
