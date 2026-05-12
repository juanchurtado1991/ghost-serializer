@file:OptIn(ExperimentalStdlibApi::class, InternalGhostApi::class)
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
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.sun.management.ThreadMXBean
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
    val runs = args.indexOf("--runs")
        .let { if (it != -1 && it + 1 < args.size) args[it + 1].toIntOrNull() ?: 1 else 1 }
    val noTests = args.contains("--no-tests")
    val warmupIters = args.indexOf("--warmup")
        .let { if (it != -1 && it + 1 < args.size) args[it + 1].toIntOrNull() ?: 15000 else 15000 }

    println("\n--- INITIALIZING PERFORMANCE BENCHMARKS (Runs: $runs) ---")
    val threadBean = initializePlatformDiagnostics() ?: return

    val gson = Gson()
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val kJson = Json { ignoreUnknownKeys = true }
    val jackson = ObjectMapper().registerModule(KotlinModule.Builder().build())

    val smallComplex = generateComplexData(20)
    val smallJson = generateNeutralJson(smallComplex)
    val smallBytes = smallJson.encodeUtf8()

    // Cold start is measured once
    val coldMetrics = runColdStart(smallBytes, jackson)
    printRankedTable("COLD START (first parse, before JUnit suite)", coldMetrics)

    runWarmup(gson, moshi, kJson, jackson, smallBytes, smallComplex, warmupIters)

    val sessions = mutableListOf<BenchmarkSessionResults>()

    repeat(runs) { i ->
        if (runs > 1) println("[Run ${i + 1}/$runs] Benchmarking...")

        val listMediumComplex = generateComplexData(200)
        val listMediumJson = generateNeutralJson(listMediumComplex)
        val listMediumBytes = listMediumJson.encodeUtf8()
        val listMediumMetrics =
            runDeserializationAllModes(threadBean, gson, moshi, kJson, jackson, listMediumBytes)

        val syncLargeComplex = generateComplexData(2000)
        val syncLargeJson = generateNeutralJson(syncLargeComplex)
        val syncLargeBytes = syncLargeJson.encodeUtf8()
        val syncLargeMetrics =
            runDeserializationAllModes(threadBean, gson, moshi, kJson, jackson, syncLargeBytes)

        val writingComplex = generateComplexData(1000)
        val writingMetrics =
            runSerializationAllModes(threadBean, gson, moshi, kJson, jackson, writingComplex)

        val stressMetrics = runStressTests(gson, moshi, kJson, jackson)
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

    // Average results across all sessions
    val finalResults = if (runs > 1) {
        BenchmarkSessionResults(
            listMedium = averageModeMetrics(sessions.map { it.listMedium }),
            syncLarge = averageModeMetrics(sessions.map { it.syncLarge }),
            writing = averageModeMetrics(sessions.map { it.writing }),
            stress = StressMetrics(
                nesting = averageMetrics(sessions.map { it.stress.nesting }),
                large = averageMetrics(sessions.map { it.stress.large })
            ),
            failure = averageMetrics(sessions.map { it.failure })
        )
    } else sessions.last()

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

    if (!noTests) {
        val testResults = ParsingTestBenchmark.runAllTests()
        ParsingTestBenchmark.printUnifiedSummaryTable(testResults)
    }

    println("\n[COMPLETE] Benchmark execution finished.")
    exitProcess(0)
}

private data class BenchmarkSessionResults(
    val listMedium: ModeMetrics,
    val syncLarge: ModeMetrics,
    val writing: ModeMetrics,
    val stress: StressMetrics,
    val failure: BenchmarkMetrics
)

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

    // Calculate ST DEV if more than 1 sample
    val stDevNanos = if (list.size > 1) {
        val avg = avgNanos / 1_000_000.0
        val variance = list.map { (it.nanos / 1_000_000.0 - avg).let { d -> d * d } }.average()
        (sqrt(variance) * 1_000_000.0).toLong()
    } else 0L

    return BenchResult(avgNanos, avgBytes, stDevNanos)
}

private data class ModeMetrics(
    val string: BenchmarkMetrics,
    val bytes: BenchmarkMetrics,
    val streaming: BenchmarkMetrics
)

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
    val stdevNanos: Long
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
        val stdevMs = rank.stdevNanos / 1_000_000.0
        val memKb = rank.mem / 1024.0

        val timeStr = if (stdevMs > 0) {
            "%7.3f ±%-5.3f".format(totalMs, stdevMs)
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
    val users = List(count) { i -> BenchUser(i, "User $i", "u@e.com", true, 1.0) }
    return ComplexResponse("success", users, meta, "42")
}

@Suppress("CheckResult")
private fun runColdStart(data: ByteString, jackson: ObjectMapper): BenchmarkMetrics {
    val coldGson = Gson()
    val coldMoshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val coldKser = Json { ignoreUnknownKeys = true }
    val gsonTime = measureTimeNanos {
        coldGson.fromJson<ComplexResponse>(
            GsonReader(
                InputStreamReader(ByteArrayInputStream(data.toByteArray()))
            ), ComplexResponse::class.java
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

@Suppress("CheckResult")
private fun runWarmup(
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    jackson: ObjectMapper,
    data: ByteString,
    complex: ComplexResponse,
    iters: Int
) {
    println("\n🔥 Warming up JIT ($iters iterations × 3 modes)...")
    val jsonString = data.utf8()
    val rawBytes = data.toByteArray()
    val moshiAdapter = moshi.adapter<ComplexResponse>()
    repeat(iters) {
        gson.fromJson<ComplexResponse>(
            jsonString,
            ComplexResponse::class.java
        ); moshiAdapter.fromJson(jsonString); kJson.decodeFromString<ComplexResponse>(jsonString); jackson.readValue<ComplexResponse>(
        jsonString
    ); Ghost.deserialize<ComplexResponse>(jsonString)
        gson.toJson(complex); moshiAdapter.toJson(complex); kJson.encodeToString(complex); jackson.writeValueAsString(
        complex
    ); Ghost.encodeToString(complex)
        gson.fromJson<ComplexResponse>(
            String(rawBytes, Charsets.UTF_8),
            ComplexResponse::class.java
        ); moshiAdapter.fromJson(
        String(
            rawBytes,
            Charsets.UTF_8
        )
    ); kJson.decodeFromString<ComplexResponse>(
        String(
            rawBytes,
            Charsets.UTF_8
        )
    ); jackson.readValue<ComplexResponse>(rawBytes); Ghost.deserialize<ComplexResponse>(rawBytes)
        gson.toJson(complex).toByteArray(); moshiAdapter.toJson(complex)
        .toByteArray(); kJson.encodeToString(complex).toByteArray(); jackson.writeValueAsBytes(
        complex
    ); Ghost.encodeToBytes(complex)
        gson.fromJson<ComplexResponse>(
            GsonReader(InputStreamReader(ByteArrayInputStream(rawBytes))),
            ComplexResponse::class.java
        ); moshiAdapter.fromJson(Buffer().write(rawBytes)); kJson.decodeFromBufferedSource<ComplexResponse>(
        Buffer().write(rawBytes)
    ); jackson.readValue<ComplexResponse>(ByteArrayInputStream(rawBytes)); Ghost.deserialize<ComplexResponse>(
        Buffer().write(rawBytes)
    )
        ByteArrayOutputStream().also { os ->
            GsonWriter(OutputStreamWriter(os)).use { w ->
                gson.toJson(
                    complex,
                    ComplexResponse::class.java,
                    w
                )
            }
        }
        Buffer().also {
            moshiAdapter.toJson(
                it,
                complex
            )
        }; Buffer().also {
        kJson.encodeToBufferedSink(
            complex,
            it
        )
    }; ByteArrayOutputStream().also {
        jackson.writeValue(
            it,
            complex
        )
    }; Buffer().also { Ghost.serialize(it, complex) }
    }
}

private fun runDeserializationAllModes(
    threadBean: ThreadMXBean,
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    jackson: ObjectMapper,
    data: ByteString
): ModeMetrics {
    val rawBytes = data.toByteArray();
    val jsonString = data.utf8();
    val moshiAdapter = moshi.adapter<ComplexResponse>()
    val stringMetrics = run {
        val gsonTime = measurePerf(threadBean) {
            gson.fromJson<ComplexResponse>(
                jsonString,
                ComplexResponse::class.java
            )
        }
        val moshiTime = measurePerf(threadBean) { moshiAdapter.fromJson(jsonString) }
        val kserTime =
            measurePerf(threadBean) { kJson.decodeFromString<ComplexResponse>(jsonString) }
        val jacksonTime = measurePerf(threadBean) { jackson.readValue<ComplexResponse>(jsonString) }
        val ghostTime = measurePerf(threadBean) { Ghost.deserialize<ComplexResponse>(jsonString) }
        BenchmarkMetrics(
            gson = BenchResult(gsonTime.second, gsonTime.third),
            moshi = BenchResult(moshiTime.second, moshiTime.third),
            kser = BenchResult(kserTime.second, kserTime.third),
            jackson = BenchResult(jacksonTime.second, jacksonTime.third),
            ghost = BenchResult(ghostTime.second, ghostTime.third)
        )
    }
    val bytesMetrics = run {
        val gsonTime = measurePerf(threadBean) {
            gson.fromJson<ComplexResponse>(
                String(
                    rawBytes,
                    Charsets.UTF_8
                ), ComplexResponse::class.java
            )
        }
        val moshiTime =
            measurePerf(threadBean) { moshiAdapter.fromJson(String(rawBytes, Charsets.UTF_8)) }
        val kserTime = measurePerf(threadBean) {
            kJson.decodeFromString<ComplexResponse>(
                String(
                    rawBytes,
                    Charsets.UTF_8
                )
            )
        }
        val jacksonTime = measurePerf(threadBean) { jackson.readValue<ComplexResponse>(rawBytes) }
        val ghostTime = measurePerf(threadBean) { Ghost.deserialize<ComplexResponse>(rawBytes) }
        BenchmarkMetrics(
            gson = BenchResult(gsonTime.second, gsonTime.third),
            moshi = BenchResult(moshiTime.second, moshiTime.third),
            kser = BenchResult(kserTime.second, kserTime.third),
            jackson = BenchResult(jacksonTime.second, jacksonTime.third),
            ghost = BenchResult(ghostTime.second, ghostTime.third)
        )
    }
    val streamingMetrics = run {
        val gsonTime = measurePerf(threadBean) {
            gson.fromJson<ComplexResponse>(
                GsonReader(
                    InputStreamReader(ByteArrayInputStream(rawBytes))
                ), ComplexResponse::class.java
            )
        }
        val moshiTime = measurePerf(threadBean) { moshiAdapter.fromJson(Buffer().write(rawBytes)) }
        val kserTime = measurePerf(threadBean) {
            kJson.decodeFromBufferedSource<ComplexResponse>(
                Buffer().write(rawBytes)
            )
        }
        val jacksonTime = measurePerf(threadBean) {
            jackson.readValue<ComplexResponse>(
                ByteArrayInputStream(rawBytes)
            )
        }
        val ghostTime =
            measurePerf(threadBean) { Ghost.deserialize<ComplexResponse>(Buffer().write(rawBytes)) }
        BenchmarkMetrics(
            gson = BenchResult(gsonTime.second, gsonTime.third),
            moshi = BenchResult(moshiTime.second, moshiTime.third),
            kser = BenchResult(kserTime.second, kserTime.third),
            jackson = BenchResult(jacksonTime.second, jacksonTime.third),
            ghost = BenchResult(ghostTime.second, ghostTime.third)
        )
    }
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
    val stringMetrics = run {
        val gsonTime = measurePerf(threadBean) { gson.toJson(complex) }
        val moshiTime = measurePerf(threadBean) { moshiAdapter.toJson(complex) }
        val kserTime = measurePerf(threadBean) { kJson.encodeToString(complex) }
        val jacksonTime = measurePerf(threadBean) { jackson.writeValueAsString(complex) }
        val ghostTime = measurePerf(threadBean) { Ghost.encodeToString(complex) }
        BenchmarkMetrics(
            gson = BenchResult(gsonTime.second, gsonTime.third),
            moshi = BenchResult(moshiTime.second, moshiTime.third),
            kser = BenchResult(kserTime.second, kserTime.third),
            jackson = BenchResult(jacksonTime.second, jacksonTime.third),
            ghost = BenchResult(ghostTime.second, ghostTime.third)
        )
    }
    val bytesMetrics = run {
        val gsonTime = measurePerf(threadBean) { gson.toJson(complex).toByteArray() }
        val moshiTime = measurePerf(threadBean) { moshiAdapter.toJson(complex).toByteArray() }
        val kserTime = measurePerf(threadBean) { kJson.encodeToString(complex).toByteArray() }
        val jacksonTime = measurePerf(threadBean) { jackson.writeValueAsBytes(complex) }
        val ghostTime = measurePerf(threadBean) { Ghost.encodeToBytes(complex) }
        BenchmarkMetrics(
            gson = BenchResult(gsonTime.second, gsonTime.third),
            moshi = BenchResult(moshiTime.second, moshiTime.third),
            kser = BenchResult(kserTime.second, kserTime.third),
            jackson = BenchResult(jacksonTime.second, jacksonTime.third),
            ghost = BenchResult(ghostTime.second, ghostTime.third)
        )
    }
    val streamingMetrics = run {
        val gsonTime = measurePerf(threadBean) {
            val os = ByteArrayOutputStream(); GsonWriter(OutputStreamWriter(os)).use { w ->
            gson.toJson(
                complex,
                ComplexResponse::class.java,
                w
            )
        }; os
        }
        val moshiTime =
            measurePerf(threadBean) { val buf = Buffer(); moshiAdapter.toJson(buf, complex); buf }
        val kserTime = measurePerf(threadBean) {
            val buf = Buffer(); kJson.encodeToBufferedSink(
            complex,
            buf
        ); buf
        }
        val jacksonTime = measurePerf(threadBean) {
            val os = ByteArrayOutputStream(); jackson.writeValue(
            os,
            complex
        ); os
        }
        val ghostTime =
            measurePerf(threadBean) { val buf = Buffer(); Ghost.serialize(buf, complex); buf }
        BenchmarkMetrics(
            gson = BenchResult(gsonTime.second, gsonTime.third),
            moshi = BenchResult(moshiTime.second, moshiTime.third),
            kser = BenchResult(kserTime.second, kserTime.third),
            jackson = BenchResult(jacksonTime.second, jacksonTime.third),
            ghost = BenchResult(ghostTime.second, ghostTime.third)
        )
    }
    return ModeMetrics(stringMetrics, bytesMetrics, streamingMetrics)
}

@Suppress("CheckResult")
private fun runStressTests(
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    jackson: ObjectMapper
): StressMetrics {
    val tree = createTree(20);
    val treeJson = generateNeutralJson(tree);
    val treeBytes = treeJson.encodeUtf8()
    val gsonTree = measurePerfSimpleNanos {
        gson.fromJson<Category>(
            GsonReader(
                InputStreamReader(ByteArrayInputStream(treeBytes.toByteArray()))
            ), Category::class.java
        )
    }
    val moshiTree = measurePerfSimpleNanos {
        moshi.adapter<Category>().fromJson(Buffer().write(treeBytes.toByteArray()))
    }
    val kSerTree = measurePerfSimpleNanos { kJson.decodeFromString<Category>(treeBytes.utf8()) }
    val jacksonTree = measurePerfSimpleNanos { jackson.readValue<Category>(treeBytes.utf8()) }
    val ghostTree = measurePerfSimpleNanos { Ghost.deserialize<Category>(treeBytes.toByteArray()) }
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
    val malformed = data.utf8().substring(0, data.size / 2);
    val bytes = malformed.encodeUtf8();
    val gson = Gson();
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build();
    val kser = Json { ignoreUnknownKeys = true };
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

private inline fun measureAvgFailSpeed(block: () -> Unit): Long {
    val startTime =
        System.nanoTime(); repeat(100) { block() }; return (System.nanoTime() - startTime) / 100
}

private fun createTree(d: Int): Category = if (d <= 0) Category(name = "L") else Category(
    name = "N",
    subCategories = listOf(createTree(d - 1))
)

@Volatile
var blackHoleSink: Any? = null
fun consume(obj: Any?) {
    blackHoleSink = obj
}

private inline fun measureTimeNanos(block: () -> Unit): Long {
    val s = System.nanoTime(); block(); return (System.nanoTime() - s)
}

private inline fun <T> measurePerf(bean: ThreadMXBean, block: () -> T): Triple<T, Long, Long> {
    val sA = bean.getThreadAllocatedBytes(Thread.currentThread().id);
    val s = System.nanoTime();
    val result = block();
    val e = System.nanoTime();
    val eA =
        bean.getThreadAllocatedBytes(Thread.currentThread().id); consume(result); return Triple(
        result,
        (e - s),
        (eA - sA)
    )
}

private inline fun measurePerfSimpleNanos(block: () -> Unit): Long {
    val s = System.nanoTime(); block(); return (System.nanoTime() - s)
}
