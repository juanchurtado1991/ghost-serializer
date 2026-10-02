@file:OptIn(InternalGhostApi::class, ExperimentalSerializationApi::class)

package com.ghost.benchmark

import com.ghost.benchmark.TwitterBenchmark.run
import com.ghost.benchmark.TwitterBenchmark.warmupGlobal
import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.TwitterResponse
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import kotlinx.serialization.serializer
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

            Ghost.decodeFromSource(source = Buffer().write(rawBytes), clazz = TwitterResponse::class)
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
            label = "Local warmup (${BenchmarkStandard.LOCAL_WARMUP_ITERATIONS} iterations before measure)"
        )
        BenchmarkProgress.repeatWithProgress(
            label = "Twitter local",
            total = BenchmarkStandard.LOCAL_WARMUP_ITERATIONS
        ) {
            ctx.runWarmupIteration()
        }

        performPhaseGc()

        val ghostSerializer = Ghost.getSerializer(TwitterResponse::class)!!
        val kserSerializer = ctx.kJson.serializersModule.serializer<TwitterResponse>()
        val moshiAdapter = ctx.moshiAdapter
        val jsonString = ctx.jsonString
        val rawBytes = ctx.rawBytes
        val decodedObj = ctx.decodedObj

        BenchmarkProgress.logStep(label = "Measuring 6 categories × ${BenchmarkStandard.MEASUREMENT_RUNS} runs")

        performPhaseGc()
        BenchmarkProgress.logStep(label = "Decode (String)")
        val ghostDecodeStr = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.deserialize(ghostSerializer, jsonString)
        }
        performPhaseGc()
        val kserDecodeStr = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            ctx.kJson.decodeFromString(kserSerializer, jsonString)
        }
        performPhaseGc()
        val moshiDecodeStr = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            moshiAdapter.fromJson(jsonString)
        }

        performPhaseGc()
        BenchmarkProgress.logStep(label = "Decode (Bytes)")
        val ghostDecodeBytes = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.deserialize(ghostSerializer, rawBytes)
        }
        performPhaseGc()
        val kserDecodeBytes = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            ctx.kJson.decodeFromString(kserSerializer, String(rawBytes, Charsets.UTF_8))
        }
        performPhaseGc()
        val moshiDecodeBytes = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            moshiAdapter.fromJson(String(rawBytes, Charsets.UTF_8))
        }

        performPhaseGc()
        BenchmarkProgress.logStep(label = "Decode (Streaming)")
        val ghostDecodeStream = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.deserializeStreaming(serializer = ghostSerializer, source = Buffer().write(rawBytes))
        }
        performPhaseGc()
        val kserDecodeStream = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            ctx.kJson.decodeFromBufferedSource(kserSerializer, Buffer().write(rawBytes))
        }
        performPhaseGc()
        val moshiDecodeStream = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            moshiAdapter.fromJson(JsonReader.of(Buffer().write(rawBytes)))
        }

        performPhaseGc()
        BenchmarkProgress.logStep(label = "Encode (String)")
        val ghostEncodeStr = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.encodeToString(serializer = ghostSerializer, value = decodedObj)
        }
        performPhaseGc()
        val kserEncodeStr = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            ctx.kJson.encodeToString(serializer = kserSerializer, value = decodedObj)
        }
        performPhaseGc()
        val moshiEncodeStr = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            moshiAdapter.toJson(decodedObj)
        }

        performPhaseGc()
        BenchmarkProgress.logStep(label = "Encode (Bytes)")
        val ghostEncodeBytes = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.encodeToBytes(serializer = ghostSerializer, value = decodedObj)
        }
        performPhaseGc()
        val kserEncodeBytes = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            ctx.kJson.encodeToString(serializer = kserSerializer, value = decodedObj).toByteArray()
        }
        performPhaseGc()
        val moshiEncodeBytes = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            moshiAdapter.toJson(decodedObj).encodeToByteArray()
        }

        performPhaseGc()
        BenchmarkProgress.logStep(label = "Encode (Streaming)")
        val ghostEncodeStream = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            val buf = Buffer()
            Ghost.serialize(ghostSerializer, buf, decodedObj)
            buf
        }
        performPhaseGc()
        val kserEncodeStream = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            val buf = Buffer()
            ctx.kJson.encodeToBufferedSink(kserSerializer, decodedObj, buf)
            buf
        }
        performPhaseGc()
        val moshiEncodeStream = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            val buf = Buffer()
            JsonWriter.of(buf).use { writer ->
                moshiAdapter.toJson(writer, decodedObj)
            }
            buf
        }

        printResults(
            categories = listOf(
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
            observed(category = RegressionCalculator.DECODE_STRING, ghost = ghostDecodeStr, kser = kserDecodeStr),
            observed(category = RegressionCalculator.DECODE_BYTES, ghost = ghostDecodeBytes, kser = kserDecodeBytes),
            observed(
                category = RegressionCalculator.DECODE_STREAMING,
                ghost = ghostDecodeStream,
                kser = kserDecodeStream
            ),
            observed(category = RegressionCalculator.ENCODE_STRING, ghost = ghostEncodeStr, kser = kserEncodeStr),
            observed(category = RegressionCalculator.ENCODE_BYTES, ghost = ghostEncodeBytes, kser = kserEncodeBytes),
            observed(
                category = RegressionCalculator.ENCODE_STREAMING,
                ghost = ghostEncodeStream,
                kser = kserEncodeStream
            ),
        )
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
        BenchmarkProgress.logStep(label = "Twitter macro (string / bytes / streaming × Ghost + Moshi + KSER)")
        BenchmarkProgress.repeatWithProgress(label = "Global Twitter", total = iterations) {
            ctx.runWarmupIteration()
        }
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
            "  Payload: %d bytes → µs/op and decimal GB/s (ops/s × payload / 10⁹)".format(
                payloadBytes
            )
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
                val micros = BenchmarkThroughput.opsPerSecToMicros(opsPerSec = ops)
                val microsStdev = if (ops <= 0.0) {
                    0.0
                } else {
                    micros * (opsStdev / ops)
                }
                val gb = BenchmarkThroughput.opsPerSecToGbPerSec(opsPerSec = ops, payloadBytes = payloadBytes)
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

}
