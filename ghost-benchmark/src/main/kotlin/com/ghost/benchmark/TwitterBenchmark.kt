@file:OptIn(InternalGhostApi::class, ExperimentalSerializationApi::class)

package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.TwitterResponse
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer

/**
 * Twitter macro-dataset benchmark comparing Ghost vs KotlinX Serialization.
 *
 * Measures throughput (ops/s) and memory allocation (KB/op) across 6 categories:
 * String / Bytes / Streaming × Decode / Encode
 */
object TwitterBenchmark {

    private const val NANOSECONDS_IN_SECOND = 1_000_000_000.0

    fun run(runs: Int, warmupIters: Int, threadBean: ThreadMXBean?) {
        println("\n========================================================")
        println("BENCHMARK: TWITTER MACRO DATASET")
        println("========================================================")

        val resource = object {}.javaClass.classLoader.getResource("twitter_macro.json")
        if (resource == null) {
            println("  ⚠️  Skipping Twitter benchmark: twitter_macro.json not found.")
            return
        }

        val jsonString = resource.readText()
        val rawBytes = jsonString.encodeToByteArray()
        val stringFromBytes = String(rawBytes, Charsets.UTF_8)
        val kJson = Json { ignoreUnknownKeys = true }
        val decodedObj = Ghost.deserialize<TwitterResponse>(jsonString)

        // Warmup — uses the same dynamic iteration count from CLI args
        println("🔥 Warming up Twitter models ($warmupIters iterations)...")
        repeat(warmupIters) { i ->
            if (warmupIters > 1 && (i + 1) % 5000 == 0) {
                println("   [Warmup ${i + 1}/$warmupIters]...")
            }
            // String Mode
            Ghost.deserialize<TwitterResponse>(jsonString)
            kJson.decodeFromString<TwitterResponse>(jsonString)
            Ghost.encodeToString(decodedObj)
            kJson.encodeToString(decodedObj)

            // Bytes Mode
            Ghost.deserialize<TwitterResponse>(rawBytes)
            kJson.decodeFromString<TwitterResponse>(stringFromBytes)
            Ghost.encodeToBytes(decodedObj)
            kJson.encodeToString(decodedObj).toByteArray()

            // Streaming Mode
            Ghost.decodeFromSource(Buffer().write(rawBytes), TwitterResponse::class)
            kJson.decodeFromBufferedSource<TwitterResponse>(Buffer().write(rawBytes))
            Buffer().also { Ghost.serialize(it, decodedObj) }
            Buffer().also { kJson.encodeToBufferedSink(decodedObj, it) }
        }
        println("Done.")

        // Clean heap before starting the measured benchmarks
        System.gc()
        System.runFinalization()
        Thread.sleep(100)
        System.gc()

        println("\n🚀 Running Twitter performance measurements ($runs iterations per category)...")

        val ghostSerializer = Ghost.getSerializer(TwitterResponse::class)!!
        val kserSerializer = kJson.serializersModule.serializer<TwitterResponse>()

        // Decode Benchmarks
        val ghostDecodeStr = measurePerf(threadBean, runs) { Ghost.deserialize(ghostSerializer, jsonString) }
        val kserDecodeStr = measurePerf(threadBean, runs) { kJson.decodeFromString(kserSerializer, jsonString) }

        val ghostDecodeBytes = measurePerf(threadBean, runs) { Ghost.deserialize(ghostSerializer, rawBytes) }
        val kserDecodeBytes = measurePerf(threadBean, runs) {
            kJson.decodeFromString(kserSerializer, String(rawBytes, Charsets.UTF_8))
        }

        val ghostDecodeStream = measurePerf(threadBean, runs) {
            Ghost.deserializeStreaming(ghostSerializer, Buffer().write(rawBytes))
        }
        val kserDecodeStream = measurePerf(threadBean, runs) {
            kJson.decodeFromBufferedSource(kserSerializer, Buffer().write(rawBytes))
        }

        // Encode Benchmarks
        val ghostEncodeStr = measurePerf(threadBean, runs) { Ghost.encodeToString(ghostSerializer, decodedObj) }
        val kserEncodeStr = measurePerf(threadBean, runs) { kJson.encodeToString(kserSerializer, decodedObj) }

        val ghostEncodeBytes = measurePerf(threadBean, runs) { Ghost.encodeToBytes(ghostSerializer, decodedObj) }
        val kserEncodeBytes = measurePerf(threadBean, runs) { kJson.encodeToString(kserSerializer, decodedObj).toByteArray() }

        val ghostEncodeStream = measurePerf(threadBean, runs) {
            val buf = Buffer()
            Ghost.serialize(ghostSerializer, buf, decodedObj)
            buf
        }
        val kserEncodeStream = measurePerf(threadBean, runs) {
            val buf = Buffer()
            kJson.encodeToBufferedSink(kserSerializer, decodedObj, buf)
            buf
        }

        printResults(
            listOf(
                "Decode (String)" to (ghostDecodeStr to kserDecodeStr),
                "Decode (Bytes)" to (ghostDecodeBytes to kserDecodeBytes),
                "Decode (Streaming)" to (ghostDecodeStream to kserDecodeStream),
                "Encode (String)" to (ghostEncodeStr to kserEncodeStr),
                "Encode (Bytes)" to (ghostEncodeBytes to kserEncodeBytes),
                "Encode (Streaming)" to (ghostEncodeStream to kserEncodeStream)
            )
        )
    }

    private fun printResults(categories: List<Pair<String, Pair<Triple<Double, Double, Double>, Triple<Double, Double, Double>>>>) {
        println("\n--- Twitter Dataset Performance Summary (Fastest First) ---")
        println("| Operation          | Engine | Throughput (ops/s) |  StDev (ops/s) | Mem (KB/op) |")
        println("|--------------------|--------|---------------------|----------------|-------------|")
        for ((label, scores) in categories) {
            val sorted = listOf(
                "GHOST" to scores.first,
                "KSER" to scores.second
            ).sortedByDescending { it.second.first }
            for (res in sorted) {
                println("| %-18s | %-6s | %19.3f | %14.3f | %11.1f |".format(
                    label, res.first, res.second.first, res.second.second, res.second.third
                ))
            }
            val winner = sorted[0]
            val loser = sorted[1]
            val pct = ((winner.second.first - loser.second.first) / loser.second.first) * 100.0
            val memPct = if (loser.second.third > 0) {
                ((loser.second.third - winner.second.third) / loser.second.third) * 100.0
            } else {
                0.0
            }
            val memString = if (memPct >= 0.0) {
                "%.1f%% less memory".format(memPct)
            } else {
                "but uses %.1f%% MORE memory".format(-memPct)
            }
            println(
                "   👉 WINNER for %s: %s (%.1f%% faster, %s than %s)".format(
                    label, winner.first, pct, memString, loser.first
                )
            )
            println("|--------------------|--------|---------------------|----------------|-------------|")
        }
    }

    @Volatile
    private var blackHoleSink: Any? = null
    private fun consume(obj: Any?) {
        blackHoleSink = obj
    }

    private inline fun <T> measurePerf(
        threadBean: ThreadMXBean?,
        runs: Int,
        crossinline block: () -> T
    ): Triple<Double, Double, Double> {
        val numBatches = if (runs >= 10) 10 else 1
        val runsPerBatch = runs / numBatches

        val currentThreadId = Thread.currentThread().id
        val startAllocatedBytes = threadBean?.getThreadAllocatedBytes(currentThreadId) ?: 0L
        val startTime = System.nanoTime()

        val batchThroughputs = DoubleArray(numBatches)
        repeat(numBatches) { b ->
            val start = System.nanoTime()
            repeat(runsPerBatch) {
                val res = block()
                consume(res)
            }
            val elapsed = System.nanoTime() - start
            val batchThroughput = runsPerBatch / (elapsed.toDouble() / NANOSECONDS_IN_SECOND)
            batchThroughputs[b] = batchThroughput
        }

        val elapsedNanos = System.nanoTime() - startTime
        val endAllocatedBytes = threadBean?.getThreadAllocatedBytes(currentThreadId) ?: 0L

        val avgThroughput = runs / (elapsedNanos.toDouble() / NANOSECONDS_IN_SECOND)

        // Calculate Standard Deviation of Throughput
        val stdDev = if (numBatches > 1) {
            val mean = batchThroughputs.average()
            val variance = batchThroughputs.map { (it - mean) * (it - mean) }.sum() / (numBatches - 1)
            kotlin.math.sqrt(variance)
        } else {
            0.0
        }

        val allocatedBytes = endAllocatedBytes - startAllocatedBytes
        val kbPerOp = if (allocatedBytes > 0) (allocatedBytes.toDouble() / runs) / 1024.0 else 0.0

        return Triple(avgThroughput, stdDev, kbPerOp)
    }
}
