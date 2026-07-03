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
 *
 * JIT is warmed globally in [warmupGlobal] (phase 2); [run] only runs a short local warmup
 * before measurement.
 */
object TwitterBenchmark {

    private const val NANOSECONDS_IN_SECOND = 1_000_000_000.0

    private data class WarmupContext(
        val jsonString: String,
        val rawBytes: ByteArray,
        val stringFromBytes: String,
        val kJson: Json,
        val decodedObj: TwitterResponse,
    ) {
        fun runWarmupIteration() {
            Ghost.deserialize<TwitterResponse>(jsonString)
            kJson.decodeFromString<TwitterResponse>(jsonString)
            Ghost.encodeToString(decodedObj)
            kJson.encodeToString(decodedObj)

            Ghost.deserialize<TwitterResponse>(rawBytes)
            kJson.decodeFromString<TwitterResponse>(stringFromBytes)
            Ghost.encodeToBytes(decodedObj)
            kJson.encodeToString(decodedObj).toByteArray()

            Ghost.decodeFromSource(Buffer().write(rawBytes), TwitterResponse::class)
            kJson.decodeFromBufferedSource<TwitterResponse>(Buffer().write(rawBytes))
            Buffer().also { Ghost.serialize(it, decodedObj) }
            Buffer().also { kJson.encodeToBufferedSink(decodedObj, it) }
        }
    }

    /**
     * Global JIT warmup for Twitter paths — called once from [GhostBenchmark] phase 2.
     */
    fun warmupGlobal(iterations: Int) {
        val ctx = loadWarmupContext() ?: return
        BenchmarkProgress.logStep("Twitter macro (string / bytes / streaming × Ghost + KSER)")
        BenchmarkProgress.repeatWithProgress("Global Twitter", iterations) {
            ctx.runWarmupIteration()
        }
    }

    fun run(threadBean: ThreadMXBean?): List<RegressionCalculator.Observed> {
        println("\n========================================================")
        println("BENCHMARK: TWITTER MACRO DATASET")
        println("========================================================")

        val ctx = loadWarmupContext() ?: return emptyList()

        BenchmarkProgress.logStep(
            "Local warmup (${BenchmarkStandard.LOCAL_WARMUP_ITERATIONS} iterations before measure)"
        )
        BenchmarkProgress.repeatWithProgress("Twitter local", BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) {
            ctx.runWarmupIteration()
        }

        cleanHeap()

        val ghostSerializer = Ghost.getSerializer(TwitterResponse::class)!!
        val kserSerializer = ctx.kJson.serializersModule.serializer<TwitterResponse>()
        val jsonString = ctx.jsonString
        val rawBytes = ctx.rawBytes
        val decodedObj = ctx.decodedObj

        BenchmarkProgress.logStep("Measuring 6 categories × ${BenchmarkStandard.MEASUREMENT_RUNS} runs")

        cleanHeap()
        BenchmarkProgress.logStep("Decode (String)")
        val ghostDecodeStr = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.deserialize(ghostSerializer, jsonString)
        }
        cleanHeap()
        val kserDecodeStr = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            ctx.kJson.decodeFromString(kserSerializer, jsonString)
        }

        cleanHeap()
        BenchmarkProgress.logStep("Decode (Bytes)")
        val ghostDecodeBytes = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.deserialize(ghostSerializer, rawBytes)
        }
        cleanHeap()
        val kserDecodeBytes = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            ctx.kJson.decodeFromString(kserSerializer, String(rawBytes, Charsets.UTF_8))
        }

        cleanHeap()
        BenchmarkProgress.logStep("Decode (Streaming)")
        val ghostDecodeStream = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.deserializeStreaming(ghostSerializer, Buffer().write(rawBytes))
        }
        cleanHeap()
        val kserDecodeStream = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            ctx.kJson.decodeFromBufferedSource(kserSerializer, Buffer().write(rawBytes))
        }

        cleanHeap()
        BenchmarkProgress.logStep("Encode (String)")
        val ghostEncodeStr = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.encodeToString(ghostSerializer, decodedObj)
        }
        cleanHeap()
        val kserEncodeStr = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            ctx.kJson.encodeToString(kserSerializer, decodedObj)
        }

        cleanHeap()
        BenchmarkProgress.logStep("Encode (Bytes)")
        val ghostEncodeBytes = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.encodeToBytes(ghostSerializer, decodedObj)
        }
        cleanHeap()
        val kserEncodeBytes = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            ctx.kJson.encodeToString(kserSerializer, decodedObj).toByteArray()
        }

        cleanHeap()
        BenchmarkProgress.logStep("Encode (Streaming)")
        val ghostEncodeStream = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            val buf = Buffer()
            Ghost.serialize(ghostSerializer, buf, decodedObj)
            buf
        }
        cleanHeap()
        val kserEncodeStream = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            val buf = Buffer()
            ctx.kJson.encodeToBufferedSink(kserSerializer, decodedObj, buf)
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

        return listOf(
            observed(RegressionCalculator.DECODE_STRING, ghostDecodeStr, kserDecodeStr),
            observed(RegressionCalculator.DECODE_BYTES, ghostDecodeBytes, kserDecodeBytes),
            observed(RegressionCalculator.DECODE_STREAMING, ghostDecodeStream, kserDecodeStream),
            observed(RegressionCalculator.ENCODE_STRING, ghostEncodeStr, kserEncodeStr),
            observed(RegressionCalculator.ENCODE_BYTES, ghostEncodeBytes, kserEncodeBytes),
            observed(RegressionCalculator.ENCODE_STREAMING, ghostEncodeStream, kserEncodeStream),
        )
    }

    private fun loadWarmupContext(): WarmupContext? {
        val resource = object {}.javaClass.classLoader.getResource("twitter_macro.json")
        if (resource == null) {
            println("  ⚠️  Skipping Twitter benchmark: twitter_macro.json not found.")
            return null
        }
        val jsonString = resource.readText()
        val rawBytes = jsonString.encodeToByteArray()
        val kJson = Json { ignoreUnknownKeys = true }
        return WarmupContext(
            jsonString = jsonString,
            rawBytes = rawBytes,
            stringFromBytes = String(rawBytes, Charsets.UTF_8),
            kJson = kJson,
            decodedObj = Ghost.deserialize<TwitterResponse>(jsonString),
        )
    }

    /** Maps a measured (throughput, stdev, KB/op) Ghost/KSER pair to a calculator observation. */
    private fun observed(
        category: String,
        ghost: Triple<Double, Double, Double>,
        kser: Triple<Double, Double, Double>,
    ): RegressionCalculator.Observed {
        return RegressionCalculator.Observed(
            group = RegressionCalculator.TWITTER,
            category = category,
            metric = RegressionCalculator.Metric.THROUGHPUT,
            ghostSpeed = ghost.first,
            kserSpeed = kser.first,
            ghostMemKb = ghost.third,
            kserMemKb = kser.third,
        )
    }

    private fun printResults(
        categories: List<Pair<String, Pair<Triple<Double, Double, Double>, Triple<Double, Double, Double>>>>
    ) {
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

    private fun cleanHeap() {
        System.gc()
        System.runFinalization()
    }
}
