@file:OptIn(ExperimentalStdlibApi::class)
@file:Suppress("SameParameterValue")

package com.ghost.benchmark

import com.ghost.serialization.integration.model.BenchResult
import com.ghost.serialization.integration.model.BenchUser
import com.ghost.serialization.integration.model.BenchmarkMetrics
import com.ghost.serialization.integration.model.Category
import com.ghost.serialization.integration.model.ComplexResponse
import com.ghost.serialization.integration.model.ExtremeMetadata
import com.ghost.serialization.integration.model.UserRole
import com.ghost.serialization.Ghost
import com.ghost.serialization.core.parser.GhostJsonReader
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import com.google.gson.stream.JsonReader as GsonReader

/**
 * Robust Performance Audit Suite for GhostSerialization.
 * Industrial Grade Console Benchmark.
 */
@OptIn(
    ExperimentalSerializationApi::class,
    ExperimentalStdlibApi::class
)
fun main() {
    println("\n🚀 INITIALIZING GHOST HYPER-PERFORMANCE AUDIT...")
    executeSafetyAudit()
    val threadBean = initializePlatformDiagnostics() ?: return

    val gson = Gson()
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val kJson = Json { ignoreUnknownKeys = true }

    // 1. Cold Start Audit (The "First Hit" impact)
    val smallComplex = generateComplexData(20)
    val smallJson = generateNeutralJson(smallComplex)
    val smallBytes = smallJson.encodeUtf8()

    val coldMetrics = runColdStart(smallBytes)
    printRankedTable("COLD START AUDIT (First Run)", coldMetrics)

    // 2. Warmup Phase (Ensuring JIT optimization)
    runWarmup(gson, moshi, kJson, smallBytes, smallComplex)

    // 3. Steady-State Workloads (Focusing on heavy lifting)
    val workloads = listOf(
        "LIST_MEDIUM" to 200,
        "SYNC_FULL_LARGE" to 2000
    )

    for ((label, count) in workloads) {
        val complex = generateComplexData(count)
        val jsonString = generateNeutralJson(complex)
        val byteData = jsonString.encodeUtf8()
        val payloadSize = "%.2f KB".format(jsonString.length / 1024.0)

        val metrics = runSteadyState(count, threadBean, gson, moshi, kJson, byteData)
        printRankedTable(
            "STEADY-STATE DESERIALIZATION: $label ($count objects | $payloadSize)",
            metrics
        )
    }

    // 4. Serialization Audit (Writing path)
    val countSer = 1000
    val largeComplex = generateComplexData(countSer)
    val serJson = generateNeutralJson(largeComplex)
    val serSize = "%.2f KB".format(serJson.length / 1024.0)

    val serMetrics = runSerializationSteadyState(threadBean, gson, moshi, kJson, largeComplex)
    printRankedTable(
        "STEADY-STATE SERIALIZATION: WRITING ($countSer objects | $serSize)",
        serMetrics
    )

    // 5. Stress Test: Deep Nesting (Recursion Impact)
    val stressMetrics = runStressTests(gson, moshi, kJson)
    printRankedTable("STRESS TEST: DEEP NESTING (20 Levels)", stressMetrics.nesting)

    // 6. Failure Resilience (Speed of error detection)
    val failureMetrics = runFailureTests(smallBytes)
    printRankedTable("FAILURE RESILIENCE (Malformed JSON)", failureMetrics)

    println("\n✅ AUDIT COMPLETE. Ghost is precision. Ghost is fluidity. Ghost is the standard.")
}

private fun printRankedTable(title: String, metrics: BenchmarkMetrics) {
    println("\n========================================================")
    println("AUDIT: $title")
    println("========================================================")

    data class EngineRank(val name: String, val nanos: Long, val mem: Long)

    val rankings = listOf(
        EngineRank("GHOST", metrics.ghost.nanos, metrics.ghost.allocBytes),
        EngineRank("MOSHI", metrics.moshi.nanos, metrics.moshi.allocBytes),
        EngineRank("KSER", metrics.kser.nanos, metrics.kser.allocBytes),
        EngineRank("GSON", metrics.gson.nanos, metrics.gson.allocBytes)
    ).sortedBy { if (it.nanos <= 0) Long.MAX_VALUE else it.nanos }

    println("| RANK | ENGINE   | TOTAL(ms)  | MEM(KB)    |")
    println("|------|----------|------------|------------|")

    rankings.forEachIndexed { index, rank ->
        val totalMs = rank.nanos / 1_000_000.0
        val memKb = rank.mem / 1024.0
        println(
            "| %-4d | %-8s | %10.3f | %10.1f |".format(
                index + 1, rank.name, totalMs, memKb
            )
        )
    }

    val winner = rankings.first()
    val slowest = rankings.last()
    if (winner.nanos > 0 && slowest.nanos > 0) {
        val speedVsSlowest = ((slowest.nanos.toDouble() / winner.nanos.toDouble()) - 1.0) * 100.0
        println(
            "🏆 WINNER: ${winner.name} (%.1f%% faster than ${slowest.name})".format(
                speedVsSlowest
            )
        )
    }
}

/**
 * Neutral Data Generation
 */
private fun generateNeutralJson(data: ComplexResponse): String {
    return Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<ComplexResponse>().toJson(data)
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
private fun runColdStart(data: ByteString): BenchmarkMetrics {
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
    val kSerializationTime = measureTimeNanos {
        coldKser.decodeFromString<ComplexResponse>(data.utf8())
    }
    val ghostTime = measureTimeNanos {
        Ghost.deserialize<ComplexResponse>(data.toByteArray())
    }

    return BenchmarkMetrics(
        gson = BenchResult(gsonTime, 0),
        moshi = BenchResult(moshiTime, 0),
        kser = BenchResult(kSerializationTime, 0),
        ghost = BenchResult(ghostTime, 0)
    )
}

@Suppress("CheckResult")
private fun runWarmup(
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    data: ByteString,
    complex: ComplexResponse
) {
    println("\n🔥 Warming up JIT (50 iterations)...")
    repeat(50) {
        gson.fromJson<ComplexResponse>(
            GsonReader(InputStreamReader(ByteArrayInputStream(data.toByteArray()))),
            ComplexResponse::class.java
        )
        moshi.adapter<ComplexResponse>().fromJson(Buffer().write(data.toByteArray()))
        kJson.decodeFromString<ComplexResponse>(data.utf8())
        Ghost.deserialize<ComplexResponse>(data.toByteArray())

        gson.toJson(complex)
        moshi.adapter<ComplexResponse>().toJson(complex)
        kJson.encodeToString(complex)
        Ghost.serialize(complex)
    }
}

private fun runSerializationSteadyState(
    threadBean: ThreadMXBean,
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    complex: ComplexResponse
): BenchmarkMetrics {
    val gsonTime = measurePerf(threadBean) { gson.toJson(complex) }
    val moshiTime = measurePerf(threadBean) { moshi.adapter<ComplexResponse>().toJson(complex) }
    val kserTime = measurePerf(threadBean) { kJson.encodeToString(complex) }
    val ghostTime = measurePerf(threadBean) {
        val buf = Buffer(); Ghost.serialize(buf, complex)
    }
    return BenchmarkMetrics(
        BenchResult(gsonTime.second, gsonTime.third),
        BenchResult(moshiTime.second, moshiTime.third),
        BenchResult(kserTime.second, kserTime.third),
        BenchResult(ghostTime.second, ghostTime.third)
    )
}

private fun runSteadyState(
    count: Int,
    threadBean: ThreadMXBean,
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    data: ByteString
): BenchmarkMetrics {
    val rawBytes = data.toByteArray()
    val gsonTime = measurePerf(threadBean) {
        gson.fromJson<ComplexResponse>(
            GsonReader(InputStreamReader(ByteArrayInputStream(rawBytes))),
            ComplexResponse::class.java
        )
    }
    val moshiTime = measurePerf(threadBean) {
        moshi.adapter<ComplexResponse>().fromJson(Buffer().write(rawBytes))
    }
    val kserTime = measurePerf(threadBean) {
        kJson.decodeFromString<ComplexResponse>(data.utf8())
    }
    val ghostReader = GhostJsonReader(rawBytes)
    val ghostTime = measurePerf(threadBean) {
        ghostReader.reset(rawBytes)
        Ghost.deserialize<ComplexResponse>(ghostReader)
    }

    return BenchmarkMetrics(
        BenchResult(gsonTime.second, gsonTime.third),
        BenchResult(moshiTime.second, moshiTime.third),
        BenchResult(kserTime.second, kserTime.third),
        BenchResult(ghostTime.second, ghostTime.third)
    )
}

@Suppress("CheckResult")
private fun runStressTests(
    gson: Gson,
    moshi: Moshi,
    kJson: Json
): com.ghost.serialization.integration.model.StressMetrics {
    val tree = createTree(20)
    val treeBytes = gson.toJson(tree).encodeUtf8()

    val gsonTree = measurePerfSimpleNanos {
        gson.fromJson<Category>(
            GsonReader(InputStreamReader(ByteArrayInputStream(treeBytes.toByteArray()))),
            Category::class.java
        )
    }
    val moshiTree = measurePerfSimpleNanos {
        moshi.adapter<Category>().fromJson(Buffer().write(treeBytes.toByteArray()))
    }
    val kSerTree = measurePerfSimpleNanos {
        kJson.decodeFromString<Category>(treeBytes.utf8())
    }
    val ghostTree = measurePerfSimpleNanos {
        Ghost.deserialize<Category>(treeBytes.toByteArray())
    }

    return com.ghost.serialization.integration.model.StressMetrics(
        nesting = BenchmarkMetrics(
            gson = BenchResult(gsonTree, 0),
            moshi = BenchResult(moshiTree, 0),
            kser = BenchResult(kSerTree, 0),
            ghost = BenchResult(ghostTree, 0)
        ),
        large = BenchmarkMetrics(
            BenchResult(0, 0),
            BenchResult(0, 0),
            BenchResult(0, 0),
            BenchResult(0, 0)
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
        ghost = BenchResult(ghostTime, 0)
    )
}

private inline fun measureAvgFailSpeed(block: () -> Unit): Long {
    val startTime = System.nanoTime()
    repeat(100) { block() }
    return (System.nanoTime() - startTime) / 100
}

private fun createTree(d: Int): Category = if (d <= 0) Category(name = "L") else Category(
    name = "N",
    subCategories = listOf(createTree(d - 1))
)

private fun executeSafetyAudit() {
    try {
        println("\n--- AUTOMATED SAFETY AUDIT ---")
        ParsingTestBenchmark.runSafetyAudit()
    } catch (e: Exception) {
        println("Audit error: ${e.message}")
    }
}

private inline fun measureTimeNanos(block: () -> Unit): Long {
    val s = System.nanoTime(); block(); return (System.nanoTime() - s)
}

private inline fun <T> measurePerf(bean: ThreadMXBean, block: () -> T): Triple<T, Long, Long> {
    val sA = bean.getThreadAllocatedBytes(Thread.currentThread().id)
    val s = System.nanoTime()
    val result = block()
    val e = System.nanoTime()
    val eA = bean.getThreadAllocatedBytes(Thread.currentThread().id)
    return Triple(result, (e - s), (eA - sA))
}

private inline fun measurePerfSimpleNanos(block: () -> Unit): Long {
    val s = System.nanoTime(); block(); return (System.nanoTime() - s)
}
