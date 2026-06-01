@file:OptIn(InternalGhostApi::class, ExperimentalSerializationApi::class)

package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.TwitterResponse
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
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
        val kJson = Json { ignoreUnknownKeys = true }
        val decodedObj = Ghost.deserialize<TwitterResponse>(jsonString)

        // Warmup — uses the same dynamic iteration count from CLI args
        print("🔥 Warming up Twitter models ($warmupIters iterations)... ")
        repeat(warmupIters) {
            // String Mode
            Ghost.deserialize<TwitterResponse>(jsonString)
            kJson.decodeFromString<TwitterResponse>(jsonString)
            Ghost.encodeToString(decodedObj)
            kJson.encodeToString(decodedObj)

            // Bytes Mode
            Ghost.deserialize<TwitterResponse>(rawBytes)
            kJson.decodeFromString<TwitterResponse>(String(rawBytes, Charsets.UTF_8))
            Ghost.encodeToBytes(decodedObj)
            kJson.encodeToString(decodedObj).toByteArray()

            // Streaming Mode
            Ghost.decodeFromSource(Buffer().write(rawBytes), TwitterResponse::class)
            kJson.decodeFromBufferedSource<TwitterResponse>(Buffer().write(rawBytes))
            Buffer().also { Ghost.serialize(it, decodedObj) }
            Buffer().also { kJson.encodeToBufferedSink(decodedObj, it) }
        }
        println("Done.")

        // Decode Benchmarks
        val ghostDecodeStr = measurePerf(threadBean, runs) { Ghost.deserialize<TwitterResponse>(jsonString) }
        val kserDecodeStr = measurePerf(threadBean, runs) { kJson.decodeFromString<TwitterResponse>(jsonString) }

        val ghostDecodeBytes = measurePerf(threadBean, runs) { Ghost.deserialize<TwitterResponse>(rawBytes) }
        val kserDecodeBytes = measurePerf(threadBean, runs) {
            kJson.decodeFromString<TwitterResponse>(String(rawBytes, Charsets.UTF_8))
        }

        val ghostDecodeStream = measurePerf(threadBean, runs) {
            Ghost.deserializeStreaming<TwitterResponse>(Buffer().write(rawBytes))
        }
        val kserDecodeStream = measurePerf(threadBean, runs) {
            kJson.decodeFromBufferedSource<TwitterResponse>(Buffer().write(rawBytes))
        }

        // Encode Benchmarks
        val ghostEncodeStr = measurePerf(threadBean, runs) { Ghost.encodeToString(decodedObj) }
        val kserEncodeStr = measurePerf(threadBean, runs) { kJson.encodeToString(decodedObj) }

        val ghostEncodeBytes = measurePerf(threadBean, runs) { Ghost.encodeToBytes(decodedObj) }
        val kserEncodeBytes = measurePerf(threadBean, runs) { kJson.encodeToString(decodedObj).toByteArray() }

        val ghostEncodeStream = measurePerf(threadBean, runs) {
            val buf = Buffer()
            Ghost.serialize(buf, decodedObj)
            buf
        }
        val kserEncodeStream = measurePerf(threadBean, runs) {
            val buf = Buffer()
            kJson.encodeToBufferedSink(decodedObj, buf)
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

    private fun printResults(categories: List<Pair<String, Pair<Pair<Double, Double>, Pair<Double, Double>>>>) {
        println("\n--- Twitter Dataset Performance Summary (Fastest First) ---")
        println("| Operation          | Engine | Throughput (ops/s) | Mem (KB/op) |")
        println("|--------------------|--------|---------------------|-------------|")
        for ((label, scores) in categories) {
            val sorted = listOf(
                "GHOST" to scores.first,
                "KSER" to scores.second
            ).sortedByDescending { it.second.first }
            for (res in sorted) {
                println("| %-18s | %-6s | %19.3f | %11.1f |".format(label, res.first, res.second.first, res.second.second))
            }
            val winner = sorted[0]
            val loser = sorted[1]
            val pct = ((winner.second.first - loser.second.first) / loser.second.first) * 100.0
            println(
                "   👉 WINNER for %s: %s (%6.1f%% faster than %s)".format(
                    label, winner.first, pct, loser.first
                )
            )
            println("|--------------------|--------|---------------------|-------------|")
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
    ): Pair<Double, Double> {
        val currentThreadId = Thread.currentThread().id
        val startAllocatedBytes = threadBean?.getThreadAllocatedBytes(currentThreadId) ?: 0L
        val startTime = System.nanoTime()

        repeat(runs) {
            val res = block()
            consume(res)
        }

        val elapsedNanos = System.nanoTime() - startTime
        val endAllocatedBytes = threadBean?.getThreadAllocatedBytes(currentThreadId) ?: 0L

        val throughput = runs / (elapsedNanos.toDouble() / NANOSECONDS_IN_SECOND)
        val allocatedBytes = endAllocatedBytes - startAllocatedBytes
        val kbPerOp = if (allocatedBytes > 0) (allocatedBytes.toDouble() / runs) / 1024.0 else 0.0

        return Pair(throughput, kbPerOp)
    }
}
