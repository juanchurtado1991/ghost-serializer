@file:OptIn(InternalGhostApi::class, ExperimentalSerializationApi::class)

package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.TwitterResponse
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer

/**
 * Twitter macro-dataset benchmark comparing Ghost vs Moshi (codegen) vs KotlinX Serialization.
 *
 * Measures throughput (µs/op + GB/s of JSON bytes) and memory allocation (KB/op)
 * across six categories: String / Bytes / Streaming × Decode / Encode.
 *
 * Global JIT warmup runs in [warmupGlobal] during [BenchmarkSuite.FULL] phase 2;
 * [run] performs a short local warmup immediately before measurement.
 */
object TwitterBenchmark {

    private const val NANOSECONDS_IN_SECOND = 1_000_000_000.0

    private data class WarmupContext(
        val jsonString: String,
        val rawBytes: ByteArray,
        val stringFromBytes: String,
        val kJson: Json,
        val moshiAdapter: com.squareup.moshi.JsonAdapter<TwitterResponse>,
        val decodedObj: TwitterResponse,
    ) {
        fun runWarmupIteration() {
            Ghost.deserialize<TwitterResponse>(jsonString)
            kJson.decodeFromString<TwitterResponse>(jsonString)
            moshiAdapter.fromJson(jsonString)
            Ghost.encodeToString(decodedObj)
            kJson.encodeToString(decodedObj)
            moshiAdapter.toJson(decodedObj)

            Ghost.deserialize<TwitterResponse>(rawBytes)
            kJson.decodeFromString<TwitterResponse>(stringFromBytes)
            moshiAdapter.fromJson(stringFromBytes)
            Ghost.encodeToBytes(decodedObj)
            kJson.encodeToString(decodedObj).toByteArray()
            moshiAdapter.toJson(decodedObj).encodeToByteArray()

            Ghost.decodeFromSource(Buffer().write(rawBytes), TwitterResponse::class)
            kJson.decodeFromBufferedSource<TwitterResponse>(Buffer().write(rawBytes))
            moshiAdapter.fromJson(JsonReader.of(Buffer().write(rawBytes)))
            Buffer().also { Ghost.serialize(it, decodedObj) }
            Buffer().also { kJson.encodeToBufferedSink(decodedObj, it) }
            Buffer().also { buf ->
                JsonWriter.of(buf).use { writer ->
                    moshiAdapter.toJson(writer, decodedObj)
                }
            }
        }
    }

    /**
     * Global JIT warmup for Twitter decode/encode paths across all I/O modes.
     *
     * Invoked from [BenchmarkSuite.FULL] phase 2 alongside the synthetic warmup.
     *
     * @param iterations number of warmup iterations (typically [BenchmarkStandard.WARMUP_ITERATIONS]).
     */
    fun warmupGlobal(iterations: Int) {
        val ctx = loadWarmupContext() ?: return
        BenchmarkProgress.logStep("Twitter macro (string / bytes / streaming × Ghost + Moshi + KSER)")
        BenchmarkProgress.repeatWithProgress("Global Twitter", iterations) {
            ctx.runWarmupIteration()
        }
    }

    /**
     * Runs the Twitter macro benchmark and returns observations for [RegressionCalculator].
     *
     * @param threadBean JVM bean for per-op allocation tracking, or `null` to skip memory metrics.
     * @return six [RegressionCalculator.Observed] rows (one per decode/encode category), or empty
     *   when `twitter_macro.json` is missing from the classpath.
     */
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
        val moshiAdapter = ctx.moshiAdapter
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
        val moshiDecodeStr = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            moshiAdapter.fromJson(jsonString)
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
        val moshiDecodeBytes = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            moshiAdapter.fromJson(String(rawBytes, Charsets.UTF_8))
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
        val moshiDecodeStream = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            moshiAdapter.fromJson(JsonReader.of(Buffer().write(rawBytes)))
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
        val moshiEncodeStr = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            moshiAdapter.toJson(decodedObj)
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
        val moshiEncodeBytes = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            moshiAdapter.toJson(decodedObj).encodeToByteArray()
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
        cleanHeap()
        val moshiEncodeStream = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            val buf = Buffer()
            JsonWriter.of(buf).use { writer ->
                moshiAdapter.toJson(writer, decodedObj)
            }
            buf
        }

        printResults(
            listOf(
                "Decode (String)" to listOf(
                    "GHOST" to ghostDecodeStr,
                    "KSER" to kserDecodeStr,
                    "MOSHI" to moshiDecodeStr,
                ),
                "Decode (Bytes)" to listOf(
                    "GHOST" to ghostDecodeBytes,
                    "KSER" to kserDecodeBytes,
                    "MOSHI" to moshiDecodeBytes,
                ),
                "Decode (Streaming)" to listOf(
                    "GHOST" to ghostDecodeStream,
                    "KSER" to kserDecodeStream,
                    "MOSHI" to moshiDecodeStream,
                ),
                "Encode (String)" to listOf(
                    "GHOST" to ghostEncodeStr,
                    "KSER" to kserEncodeStr,
                    "MOSHI" to moshiEncodeStr,
                ),
                "Encode (Bytes)" to listOf(
                    "GHOST" to ghostEncodeBytes,
                    "KSER" to kserEncodeBytes,
                    "MOSHI" to moshiEncodeBytes,
                ),
                "Encode (Streaming)" to listOf(
                    "GHOST" to ghostEncodeStream,
                    "KSER" to kserEncodeStream,
                    "MOSHI" to moshiEncodeStream,
                ),
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
        val moshi = createBenchmarkMoshi()
        return WarmupContext(
            jsonString = jsonString,
            rawBytes = rawBytes,
            stringFromBytes = String(rawBytes, Charsets.UTF_8),
            kJson = kJson,
            moshiAdapter = moshi.adapter(TwitterResponse::class.java),
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
        categories: List<Pair<String, List<Pair<String, Triple<Double, Double, Double>>>>>
    ) {
        val payloadBytes = BenchmarkThroughput.TWITTER_PAYLOAD_BYTES
        println("\n--- Twitter Dataset Performance Summary (Fastest First) ---")
        println(
            "  Payload: %d bytes → µs/op and decimal GB/s (ops/s × payload / 10⁹)".format(payloadBytes)
        )
        println(
            "| Operation          | Engine | Throughput (GB/s) | Latency (µs/op) | Mem (KB/op) |"
        )
        println(
            "|--------------------|--------|-------------------|-----------------|-------------|"
        )
        for ((label, scores) in categories) {
            val sorted = scores.sortedByDescending { it.second.first }
            for (res in sorted) {
                val ops = res.second.first
                val opsStdev = res.second.second
                val micros = BenchmarkThroughput.opsPerSecToMicros(ops)
                val microsStdev = if (ops <= 0.0) {
                    0.0
                } else {
                    micros * (opsStdev / ops)
                }
                val gb = BenchmarkThroughput.opsPerSecToGbPerSec(ops, payloadBytes)
                println(
                    "| %-18s | %-6s | %17.3f | %7.1f ±%-5.1f | %11.1f |".format(
                        label, res.first, gb, micros, microsStdev, res.second.third
                    )
                )
            }
            val winner = sorted[0]
            val slowest = sorted.last()
            val pct = ((winner.second.first - slowest.second.first) / slowest.second.first) * 100.0
            val memPct = if (slowest.second.third > 0) {
                ((slowest.second.third - winner.second.third) / slowest.second.third) * 100.0
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
                    label, winner.first, pct, memString, slowest.first
                )
            )
            println(
                "|--------------------|--------|-------------------|-----------------|-------------|"
            )
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
