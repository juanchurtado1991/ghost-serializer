@file:OptIn(
    ExperimentalStdlibApi::class, InternalGhostApi::class,
    ExperimentalSerializationApi::class
)
@file:Suppress("SameParameterValue")

package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.BenchResult
import com.ghost.serialization.integration.model.BenchmarkMetrics
import com.ghost.serialization.integration.model.Category
import com.ghost.serialization.integration.model.ComplexResponse
import com.ghost.serialization.integration.model.StressMetrics
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

/**
 * Synthetic JSON harness: LIST / SYNC / WRITING workloads, cold-start timing, and suite
 * orchestration used by [BenchmarkSuite.SYNTHETIC] and [BenchmarkSuite.FULL]. Measurement
 * plumbing lives in `GhostBenchmarkMeasurement.kt`, statistics in `GhostBenchmarkStatistics.kt`,
 * console tables in `GhostBenchmarkPresentation.kt`, and fixture generation in
 * `GhostBenchmarkFixtures.kt` — this file is only suite sequencing and per-mode measurement calls.
 *
 * CLI orchestration lives in [main] (`BenchmarkLauncher.kt`).
 */

// ============================================================================
// Phase Executors
// ============================================================================

/** Runs and prints the one-shot cold-start parse table before global JIT warmup. */
internal fun runAndPrintColdStart(smallBytes: ByteString) {
    val coldMetrics = runColdStart(data = smallBytes)
    printColdStartTable(title = "COLD START (first parse, before JUnit suite)", metrics = coldMetrics)
}

/** Executes LIST, SYNC, WRITING, stress, and failure synthetic suites; returns raw session lists. */
internal fun runSyntheticBenchmarks(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    payloads: BenchmarkPayloads,
): SyntheticRunResults {
    val listSessions = runDeserializationSuite(
        suiteLabel = "LIST_MEDIUM",
        threadBean = threadBean,
        engines = engines,
        data = payloads.listMediumBytes,
    )

    performPhaseGc()

    val syncSessions = runDeserializationSuite(
        suiteLabel = "SYNC_FULL_LARGE",
        threadBean = threadBean,
        engines = engines,
        data = payloads.syncLargeBytes,
    )

    performPhaseGc()

    val writingSessions = runSerializationSuite(
        suiteLabel = "WRITING",
        threadBean = threadBean,
        engines = engines,
        complex = payloads.writingComplex,
    )

    val stressMetrics = runStressTests(engines = engines, treeBytes = payloads.stressTreeBytes)
    val failureMetrics = runFailureTests(
        engines = engines,
        malformed = payloads.failureMalformed,
        bytes = payloads.failureBytes,
    )

    return SyntheticRunResults(
        aggregated = BenchmarkSessionResults(
            listMedium = averageModeMetrics(list = listSessions),
            syncLarge = averageModeMetrics(list = syncSessions),
            writing = averageModeMetrics(list = writingSessions),
            stress = stressMetrics,
            failure = failureMetrics,
        ),
        listSessions = listSessions,
        syncSessions = syncSessions,
        writingSessions = writingSessions,
    )
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

    BenchmarkProgress.logStep(label = "ComplexResponse (string / bytes / streaming × all engines)")
    BenchmarkProgress.repeatWithProgress(
        label = "Global ComplexResponse",
        total = BenchmarkStandard.WARMUP_ITERATIONS
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

private fun runDeserializationSuite(
    suiteLabel: String,
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    data: ByteString,
): List<ModeMetrics> {
    val rawBytes = data.toByteArray()
    val jsonString = data.utf8()
    val decodeSinks = StreamingDecodeSinks(rawBytes = rawBytes)

    return runModeMetricsSessions(label = suiteLabel) { sessionIndex ->
        ModeMetrics(
            string = measureStringDeserialization(
                threadBean = threadBean,
                engines = engines,
                jsonString = jsonString,
                sessionIndex = sessionIndex
            ),
            bytes = measureBytesDeserialization(
                threadBean = threadBean,
                engines = engines,
                rawBytes = rawBytes,
                sessionIndex = sessionIndex
            ),
            streaming = measureStreamingDeserialization(
                threadBean = threadBean,
                engines = engines,
                sinks = decodeSinks,
                sessionIndex = sessionIndex,
            ),
        )
    }
}

private fun runModeMetricsSessions(
    label: String,
    block: (sessionIndex: Int) -> ModeMetrics,
): List<ModeMetrics> {
    val sessions = mutableListOf<ModeMetrics>()
    BenchmarkProgress.repeatWithProgress(
        label = label,
        total = BenchmarkStandard.SYNTHETIC_SESSIONS
    ) { sessionIndex ->
        sessions.add(block(sessionIndex))
    }
    return sessions
}

private fun runSerializationSuite(
    suiteLabel: String,
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    complex: ComplexResponse,
): List<ModeMetrics> = runModeMetricsSessions(label = suiteLabel) { sessionIndex ->
    ModeMetrics(
        string = measureStringSerialization(
            threadBean = threadBean,
            engines = engines,
            complex = complex,
            sessionIndex = sessionIndex
        ),
        bytes = measureBytesSerialization(
            threadBean = threadBean,
            engines = engines,
            complex = complex,
            sessionIndex = sessionIndex
        ),
        streaming = measureStreamingSerialization(
            threadBean = threadBean,
            engines = engines,
            complex = complex,
            sessionIndex = sessionIndex
        ),
    )
}

/** GC between major benchmark phases only — never inside per-session hot loops. */
internal fun performPhaseGc() {
    System.gc()
    System.runFinalization()
}

// ============================================================================
// Core Execution Logic
// ============================================================================

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
        ghost = BenchResult(nanos = ghostTime, allocBytes = 0),
        kser = BenchResult(nanos = kSerializationTime, allocBytes = 0),
        moshi = BenchResult(nanos = moshiTime, allocBytes = 0),
    )
}

// ============================================================================
// Measurement Helpers: Deserialization
// ============================================================================

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

// ============================================================================
// Measurement Helpers: Serialization
// ============================================================================

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

// ============================================================================
// Stress & Failure Testing
// ============================================================================

@Suppress("CheckResult")
private fun runFailureTests(
    engines: BenchmarkEngines,
    malformed: String,
    bytes: ByteString
): BenchmarkMetrics {
    val rawBytes = bytes.toByteArray()
    val moshiAdapter = engines.complexResponseAdapter

    val moshiTime = measureAvgFailSpeed {
        runCatching { moshiAdapter.fromJson(malformed) }
    }
    val kserTime = measureAvgFailSpeed {
        runCatching { engines.kJson.decodeFromString<ComplexResponse>(malformed) }
    }
    val ghostTime = measureAvgFailSpeed {
        runCatching { Ghost.deserialize<ComplexResponse>(rawBytes) }
    }

    return BenchmarkMetrics(
        ghost = BenchResult(nanos = ghostTime, allocBytes = 0),
        kser = BenchResult(nanos = kserTime, allocBytes = 0),
        moshi = BenchResult(nanos = moshiTime, allocBytes = 0),
    )
}

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
            ghost = BenchResult(nanos = ghostTree, allocBytes = 0),
            kser = BenchResult(nanos = kSerTree, allocBytes = 0),
            moshi = BenchResult(nanos = moshiTree, allocBytes = 0),
        ),
        large = BenchmarkMetrics(
            ghost = BenchResult(nanos = 0, allocBytes = 0),
            kser = BenchResult(nanos = 0, allocBytes = 0),
            moshi = BenchResult(nanos = 0, allocBytes = 0),
        )
    )
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
