@file:OptIn(ExperimentalStdlibApi::class)
@file:Suppress("SameParameterValue")

package com.ghost.benchmark

import com.ghost.integration.model.BenchResult
import com.ghost.integration.model.BenchUser
import com.ghost.integration.model.BenchmarkMetrics
import com.ghost.integration.model.Category
import com.ghost.integration.model.ComplexResponse
import com.ghost.integration.model.ExtremeMetadata
import com.ghost.integration.model.GhostMetrics
import com.ghost.integration.model.StressMetrics
import com.ghost.integration.model.UserRole
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
 */
@OptIn(
    ExperimentalSerializationApi::class,
    ExperimentalStdlibApi::class
)
fun main() {
    executeSafetyAudit()
    val threadBean = initializePlatformDiagnostics() ?: return

    val gson = Gson()
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val kJson = Json { ignoreUnknownKeys = true }

    val count = 300_000
    val complex = generateComplexData(count)
    val jsonString = generateNeutralJson(complex)
    val byteData = jsonString.encodeUtf8()

    printInputStatistics(count, jsonString)

    val coldMetrics = runColdStart(byteData)
    runWarmup(gson, moshi, kJson, byteData, complex)

    val metrics = runSteadyState(
        count,
        threadBean,
        gson,
        moshi,
        kJson,
        byteData
    )

    val serializationMetrics = runSerializationSteadyState(
        threadBean,
        gson,
        moshi,
        kJson,
        complex
    )

    val stress = runStressTests(gson, moshi, kJson)
    val failure = runFailureTests(byteData)

    val finalMetrics = GhostMetrics(
        cold = coldMetrics,
        steady = metrics,
        serialization = serializationMetrics,
        stress = stress,
        failure = failure
    )

    printBenchmarkReport(
        count,
        "%.2f".format(byteData.size.toDouble() / 1_048_576.0),
        finalMetrics
    )
}

/**
 * Neutral Data Generation (Uses Moshi but don't measure it yet)
 */
private fun generateNeutralJson(data: ComplexResponse): String {
    return Moshi
        .Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<ComplexResponse>()
        .toJson(data)
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

@Suppress("SameParameterValue")
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
            "User $i", "u@e.com",
            true,
            1.0
        )
    }

    return ComplexResponse(
        "success",
        users,
        meta,
        "42"
    )
}

@Suppress("CheckResult")
private fun runColdStart(data: ByteString): BenchmarkMetrics {
    println("\nMeasuring Cold Start (Fair Boot)...")
    val coldGson = Gson()
    val coldMoshi = Moshi
        .Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val coldKser = Json { ignoreUnknownKeys = true }

    val gsonTime = measureTimeNanos {
        coldGson.fromJson<ComplexResponse>(
            GsonReader(InputStreamReader(ByteArrayInputStream(data.toByteArray()))),
            ComplexResponse::class.java
        )
    }
    val moshiTime = measureTimeNanos {
        coldMoshi
            .adapter<ComplexResponse>()
            .fromJson(Buffer()
                .write(data.toByteArray()))
    }

    val kSerializationTime = measureTimeNanos {
        coldKser.decodeFromString<ComplexResponse>(data.utf8())
    }

    val ghostTime = measureTimeNanos {
        Ghost.deserialize<ComplexResponse>(data.toByteArray())
    }

    return BenchmarkMetrics(
        gson = BenchResult(
            nanos = gsonTime,
            allocBytes = 0
        ),
        moshi = BenchResult(
            nanos = moshiTime,
            allocBytes = 0
        ),
        kser = BenchResult(
            nanos = kSerializationTime,
            allocBytes = 0
        ),
        ghost = BenchResult(
            nanos = ghostTime,
            allocBytes = 0
        )
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
    println("Warming up JIT (50 iterations)...")
    repeat(50) {
        gson.fromJson<ComplexResponse>(
            GsonReader(
                InputStreamReader(
                    ByteArrayInputStream(
                        data.toByteArray())
                )
            ),
            ComplexResponse::class.java
        )
        moshi.adapter<ComplexResponse>()
            .fromJson(Buffer()
                .write(data.toByteArray()))

        kJson.decodeFromString<ComplexResponse>(data.utf8())
        Ghost.deserialize<ComplexResponse>(data.toByteArray())

        // Warmup serialization
        gson.toJson(complex)
        moshi.adapter<ComplexResponse>().toJson(complex)
        kJson.encodeToString(complex)
        Ghost.serialize(complex)
    }
}

@Suppress("CheckResult")
private fun runSerializationSteadyState(
    bean: ThreadMXBean,
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    complex: ComplexResponse
): BenchmarkMetrics {
    println("Running Serialization Throughput Test...")
    val gsonTime = measurePerf(bean) { gson.toJson(complex) }
    val moshiTime = measurePerf(bean) { moshi.adapter<ComplexResponse>().toJson(complex) }
    val kserTime = measurePerf(bean) { kJson.encodeToString(complex) }
    val ghostTime = measurePerf(bean) {
        val buf = Buffer(); Ghost.serialize(buf, complex)
    }
    return BenchmarkMetrics(
        BenchResult(gsonTime.first, gsonTime.second),
        BenchResult(moshiTime.first, moshiTime.second),
        BenchResult(kserTime.first, kserTime.second),
        BenchResult(ghostTime.first, ghostTime.second)
    )
}

@Suppress("CheckResult")
private fun runSteadyState(
    count: Int,
    bean: ThreadMXBean,
    gson: Gson,
    moshi: Moshi,
    kJson: Json,
    data: ByteString
): BenchmarkMetrics {
    val rawBytes = data.toByteArray() // PRE-CONVERT ONCE
    println("Running Steady-State Throughput Test ($count objects)...")

    val gsonTime = measurePerf(bean) {
        gson.fromJson<ComplexResponse>(
            GsonReader(
                InputStreamReader(
                    ByteArrayInputStream(rawBytes)
                )
            ),
            ComplexResponse::class.java
        )
    }
    val moshiTime = measurePerf(bean) {
        moshi.adapter<ComplexResponse>()
            .fromJson(Buffer()
                .write(rawBytes))
    }
    val kserTime = measurePerf(bean) {
        kJson.decodeFromString<ComplexResponse>(data.utf8())
    }

    val ghostReader = GhostJsonReader(rawBytes)
    val ghostTime = measurePerf(bean) {
        ghostReader.reset(rawBytes)
        Ghost.deserialize<ComplexResponse>(ghostReader)
    }
    
    return BenchmarkMetrics(
        BenchResult(gsonTime.first, gsonTime.second),
        BenchResult(moshiTime.first, moshiTime.second),
        BenchResult(kserTime.first, kserTime.second),
        BenchResult(ghostTime.first, ghostTime.second)
    )
}

@Suppress("CheckResult")
private fun runStressTests(
    gson: Gson,
    moshi: Moshi,
    kJson: Json
): StressMetrics {
    val tree = createTree(20)

    val treeBytes = gson.toJson(tree).encodeUtf8()
    val gsonTree = measurePerfSimpleNanos {
        gson.fromJson<Category>(
            GsonReader(
                InputStreamReader(
                    ByteArrayInputStream(
                        treeBytes.toByteArray()
                    )
                )
            ), Category::class.java
        )
    }
    val moshiTree = measurePerfSimpleNanos {
        moshi
            .adapter<Category>()
            .fromJson(
                Buffer()
                    .copy()
                    .write(treeBytes.toByteArray())
            )
    }
    val kSerTree = measurePerfSimpleNanos {
        kJson.decodeFromString<Category>(treeBytes.utf8())
    }

    val ghostTree = measurePerfSimpleNanos {
        Ghost.deserialize<Category>(treeBytes.toByteArray())
    }

    return StressMetrics(
        nesting = BenchmarkMetrics(
            gson =BenchResult(
                nanos = gsonTree,
                allocBytes = 0
            ),
            moshi = BenchResult(
                nanos = moshiTree,
                allocBytes = 0
            ),
            kser = BenchResult(
                nanos = kSerTree,
                allocBytes = 0
            ),
            ghost = BenchResult(
                nanos = ghostTree,
                allocBytes = 0
            )
        ),
        large = BenchmarkMetrics(
            gson = BenchResult(
                nanos = 0,
                allocBytes = 0
            ),
            moshi = BenchResult(
                nanos = 0,
                allocBytes = 0
            ),
            kser = BenchResult(
                nanos = 0,
                allocBytes = 0
            ),
            ghost = BenchResult(
                nanos = 0,
                allocBytes = 0
            )
        )
    )
}

@Suppress("CheckResult")
private fun runFailureTests(data: ByteString): BenchmarkMetrics {
    val malformed = data.utf8().substring(0, data.size / 2) // Truncated JSON
    val bytes = malformed.encodeUtf8()

    val gson = Gson()
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val kser = Json { ignoreUnknownKeys = true }

    val gsonTime = measureAvgFailSpeed {
        try { gson.fromJson(malformed, ComplexResponse::class.java) }
        catch (_: Exception) { }
    }
    val moshiTime = measureAvgFailSpeed {
        try { moshi.adapter<ComplexResponse>().fromJson(malformed) }
        catch (_: Exception) { }
    }
    val kserTime = measureAvgFailSpeed {
        try { kser.decodeFromString<ComplexResponse>(malformed) }
        catch (_: Exception) { }
    }
    val ghostTime = measureAvgFailSpeed {
        try { Ghost.deserialize<ComplexResponse>(bytes.toByteArray()) }
        catch (_: Exception) { }
    }

    return BenchmarkMetrics(
        gson = BenchResult(
            nanos = gsonTime,
            allocBytes = 0
        ),
        moshi = BenchResult(
            nanos = moshiTime,
            allocBytes = 0
        ),
        kser = BenchResult(
            nanos = kserTime,
            allocBytes = 0
        ),
        ghost = BenchResult(
            nanos = ghostTime,
            allocBytes = 0
        )
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
    Category(name = "N", subCategories = listOf(createTree(d - 1)))
}

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
        ParsingTestBenchmark.runSafetyAudit()
    } catch (e: Exception) {
        println("Audit error: ${e.message}")
    }
}

private inline fun measureTimeNanos(block: () -> Unit): Long {
    val s = System.nanoTime(); block(); return (System.nanoTime() - s)
}

private inline fun measurePerf(bean: ThreadMXBean, block: () -> Unit): Pair<Long, Long> {
    val sA = bean.getThreadAllocatedBytes(Thread.currentThread().id)
    val s = System.nanoTime(); block();
    val e = System.nanoTime()
    return (e - s) to (bean.getThreadAllocatedBytes(Thread.currentThread().id) - sA)
}

private inline fun measurePerfSimpleNanos(block: () -> Unit): Long {
    val s = System.nanoTime(); block(); return (System.nanoTime() - s)
}
