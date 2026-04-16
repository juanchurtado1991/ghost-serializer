@file:OptIn(ExperimentalStdlibApi::class)
package com.ghost.benchmark

import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.Ghost
import java.util.ServiceLoader
import com.ghost.benchmark.model.*
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import com.google.gson.stream.JsonReader as GsonReader

/**
 * Robust Performance Audit Suite for GhostSerialization.
 * Standard Compliance: Rule #1 (Under 300 lines), Rule #3 (Granular methods).
 */
@OptIn(
    ExperimentalSerializationApi::class,
    ExperimentalStdlibApi::class
)
fun main() {
    executeSafetyAudit()
    val threadBean = initializePlatformDiagnostics() ?: return

    val gson = Gson()
    val moshi = Moshi.Builder().build()
    val kJson = Json { ignoreUnknownKeys = true }

    val count = 60_000
    val complex = generateComplexData(count)
    val jsonString = generateNeutralJson(complex)
    val byteData = jsonString.encodeUtf8()

    printInputStatistics(count, jsonString)

    val coldMetrics = runColdStart(byteData)
    runWarmup(gson, moshi, kJson, byteData, complex)

    val metrics = runSteadyState(threadBean, gson, moshi, kJson, byteData)
    val serializationMetrics = runSerializationSteadyState(threadBean, gson, moshi, kJson, complex)
    val stress = runStressTests(gson, moshi, kJson)
    val failure = runFailureTests(byteData)

    printBenchmarkReport(coldMetrics, metrics, serializationMetrics, stress, failure)
}

/**
 * Neutral Data Generation (Uses Moshi but don't measure it yet)
 */
private fun generateNeutralJson(data: ComplexResponse): String {
    return Moshi.Builder().build().adapter<ComplexResponse>().toJson(data)
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
    return ComplexResponse("success", users, meta, mapOf("shard" to "42"))
}

private fun runColdStart(data: ByteString): BenchmarkMetrics {
    println("\nMeasuring Cold Start (Fair Boot)...")
    val coldGson = Gson()
    val coldMoshi = Moshi.Builder().build()
    val coldKser = Json { ignoreUnknownKeys = true }

    val g = measureTime {
        coldGson.fromJson<ComplexResponse>(
            GsonReader(
                InputStreamReader(ByteArrayInputStream(data.toByteArray()))
            ), ComplexResponse::class.java
        )
    }
    val m = measureTime { coldMoshi.adapter<ComplexResponse>().fromJson(Buffer().write(data)) }
    val k =
        measureTime { coldKser.decodeFromStream<ComplexResponse>(ByteArrayInputStream(data.toByteArray())) }
    val gh = measureTime {
        Ghost.deserialize<ComplexResponse>(data.toByteArray())
    }

    return BenchmarkMetrics(Result(g, 0), Result(m, 0), Result(k, 0), Result(gh, 0))
}

private fun runWarmup(
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    data: ByteString,
    complex: ComplexResponse
) {
    println("Warming up JIT...")
    repeat(15) {
        gson.fromJson<ComplexResponse>(
            GsonReader(InputStreamReader(ByteArrayInputStream(data.toByteArray()))),
            ComplexResponse::class.java
        )
        moshi.adapter<ComplexResponse>().fromJson(Buffer().write(data))
        kJson.decodeFromStream<ComplexResponse>(ByteArrayInputStream(data.toByteArray()))
        Ghost.deserialize<ComplexResponse>(data.toByteArray())

        // Warmup serialization
        gson.toJson(complex)
        moshi.adapter<ComplexResponse>().toJson(complex)
        Json.encodeToString(ComplexResponse.serializer(), complex)
        Ghost.serialize(complex)
    }
}

private fun runSerializationSteadyState(
    bean: ThreadMXBean,
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    complex: ComplexResponse
): BenchmarkMetrics {
    println("Running Serialization Throughput Test...")
    val g = measurePerf(bean) { gson.toJson(complex) }
    val m = measurePerf(bean) { moshi.adapter<ComplexResponse>().toJson(complex) }
    val k = measurePerf(bean) { Json.encodeToString(ComplexResponse.serializer(), complex) }
    val gh = measurePerf(bean) {
        val buf = Buffer()
        Ghost.serialize(buf, complex)
    }
    return BenchmarkMetrics(
        Result(g.first, g.second),
        Result(m.first, m.second),
        Result(k.first, k.second),
        Result(gh.first, gh.second)
    )
}

internal data class BenchmarkMetrics(
    val gson: Result,
    val moshi: Result,
    val kser: Result,
    val ghost: Result
)

internal data class Result(val ms: Long, val alloc: Long)
internal data class StressMetrics(val nesting: BenchmarkMetrics, val large: BenchmarkMetrics)

private fun runSteadyState(
    bean: ThreadMXBean,
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    data: ByteString
): BenchmarkMetrics {
    println("Running Steady-State Throughput Test...")
    val g = measurePerf(bean) {
        gson.fromJson<ComplexResponse>(
            GsonReader(
                InputStreamReader(ByteArrayInputStream(data.toByteArray()))
            ), ComplexResponse::class.java
        )
    }
    val m = measurePerf(bean) { moshi.adapter<ComplexResponse>().fromJson(Buffer().write(data)) }
    val k =
        measurePerf(bean) { kJson.decodeFromStream<ComplexResponse>(ByteArrayInputStream(data.toByteArray())) }
    val gh = measurePerf(bean) { Ghost.deserialize<ComplexResponse>(data.toByteArray()) }
    return BenchmarkMetrics(
        Result(g.first, g.second),
        Result(m.first, m.second),
        Result(k.first, k.second),
        Result(gh.first, gh.second)
    )
}

private fun runStressTests(gson: Gson, moshi: Moshi, kJson: Json): StressMetrics {
    val tree = createTree(20)
    val tBytes = gson.toJson(tree).encodeUtf8()
    val gTree = measurePerfSimple {
        gson.fromJson<Category>(
            GsonReader(
                InputStreamReader(ByteArrayInputStream(tBytes.toByteArray()))
            ), Category::class.java
        )
    }
    val mTree =
        measurePerfSimple { moshi.adapter<Category>().fromJson(Buffer().copy().write(tBytes)) }
    val kTree =
        measurePerfSimple { kJson.decodeFromStream<Category>(ByteArrayInputStream(tBytes.toByteArray())) }
    val ghTree = measurePerfSimple { Ghost.deserialize<Category>(tBytes.toByteArray()) }

    return StressMetrics(
        BenchmarkMetrics(Result(gTree, 0), Result(mTree, 0), Result(kTree, 0), Result(ghTree, 0)),
        BenchmarkMetrics(Result(0, 0), Result(0, 0), Result(0, 0), Result(0, 0))
    )
}

private fun runFailureTests(data: ByteString): BenchmarkMetrics {
    val malformed = data.utf8().substring(0, data.size / 2) // Truncated JSON
    val bytes = malformed.encodeUtf8()

    val gson = Gson()
    val moshi = Moshi.Builder().build()
    val kser = Json { ignoreUnknownKeys = true }

    val g = measureAvgFailSpeed {
        try {
            gson.fromJson<ComplexResponse>(malformed, ComplexResponse::class.java)
        } catch (e: Exception) {
        }
    }
    val m = measureAvgFailSpeed {
        try {
            moshi.adapter<ComplexResponse>().fromJson(malformed)
        } catch (e: Exception) {
        }
    }
    val k = measureAvgFailSpeed {
        try {
            kser.decodeFromString<ComplexResponse>(malformed)
        } catch (e: Exception) {
        }
    }
    val gh = measureAvgFailSpeed {
        try {
            Ghost.deserialize<ComplexResponse>(bytes.toByteArray())
        } catch (e: Exception) {
        }
    }

    return BenchmarkMetrics(Result(g, 0), Result(m, 0), Result(k, 0), Result(gh, 0))
}

private inline fun measureAvgFailSpeed(block: () -> Unit): Long {
    val s = System.nanoTime()
    repeat(100) { block() }
    return (System.nanoTime() - s) / 100
}

private fun createTree(d: Int): Category =
    if (d <= 0) Category("L") else Category("N", listOf(createTree(d - 1)))

private fun printInputStatistics(count: Int, json: String) {
    println("--- INPUT STATISTICS ---")
    println(
        "Total Objects Parsed: %-10d | Payload: %-10.2f MB".format(
            count,
            json.length.toDouble() / (1024 * 1024)
        )
    )
}

private fun executeSafetyAudit() {
    try {
        println("\n--- AUTOMATED SAFETY AUDIT (RUNNING ALL PROJECT TESTS) ---")
        com.ghost.benchmark.ParsingTestBenchmark.runSafetyAudit()
    } catch (e: Exception) {
        println("Audit error: ${e.message}")
    }
}

private inline fun measureTime(block: () -> Unit): Long {
    val s = System.nanoTime(); block(); return (System.nanoTime() - s) / 1_000_000
}

private inline fun measurePerf(bean: ThreadMXBean, block: () -> Unit): Pair<Long, Long> {
    val sA = bean.getThreadAllocatedBytes(Thread.currentThread().id)
    val s = System.nanoTime(); block();
    val e = System.nanoTime()
    return (e - s) / 1_000_000 to (bean.getThreadAllocatedBytes(Thread.currentThread().id) - sA) / 1024
}

private inline fun measurePerfSimple(block: () -> Unit): Long {
    val s = System.nanoTime(); block(); return (System.nanoTime() - s) / 1_000_000
}
