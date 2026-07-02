@file:OptIn(InternalGhostApi::class)

package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.CustomCoderStressModel
import com.ghost.serialization.integration.model.FlattenedModel
import com.ghost.serialization.integration.model.OpaqueMetadataEnvelope
import com.ghost.serialization.integration.model.ResilientItem
import com.ghost.serialization.integration.model.SmartHome
import com.ghost.serialization.integration.model.SseEventEnvelopeSerializer
import com.ghost.serialization.integration.model.TagsProbe
import com.ghost.serialization.types.decodeAs
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/**
 * Ghost-only micro-benchmark for features that have no equivalent in other JSON libraries.
 *
 * These are measured independently because they cannot be compared fairly against
 * Gson, Moshi, KSer, or Jackson — they simply don't support these capabilities.
 *
 * Runs with the same ThreadMXBean methodology as the main benchmark.
 */
object GhostSpecialFeaturesBenchmark {

    private const val LABEL_SEALED = "Polymorphism — Sealed Class Dispatch"
    private const val LABEL_FLATTEN = "Structural Flattening — @GhostFlatten (3 levels deep)"
    private const val LABEL_RESILIENT = "Resilience — @GhostResilient (type mismatch recovery)"
    private const val LABEL_DECODER = "Custom Decoders — @GhostDecoder (hex + nullable transform)"
    private const val LABEL_FALLBACK = "Polymorphic Fallback — @GhostFallback (unknown discriminator)"
    private const val LABEL_RAWJSON_CAPTURE = "Opaque JSON — RawJson field capture (slice, bytes)"
    private const val LABEL_RAWJSON_KIND = "Opaque JSON — RawJson.kind() on captured slice"
    private const val LABEL_RAWJSON_DECODE_AS = "Opaque JSON — RawJson.decodeAs<T>() second stage"
    private const val LABEL_ENVELOPE_PAYLOAD = "JsonEnvelope — parsePayload (SSE fat envelope)"
    private const val LABEL_ENVELOPE_TYPED = "JsonEnvelope — parseTyped (cached serializer route)"

    private const val JSON_SEALED_1 =
        """{"id":"h1","devices":[{"type":"Light","brightness":80},{"type":"Thermostat","temperature":22.5},{"type":"Light","brightness":40}]}"""
    private const val JSON_SEALED_2 =
        """{"id":"h2","devices":[{"type":"Thermostat","temperature":18.0},{"type":"FutureDevice","data":"x"}]}"""
    private const val JSON_FLATTEN_1 =
        """{"id":1,"attributes":{"value":{"level":85},"status":"active"},"metadata":{"author":"Ghost"}}"""
    private const val JSON_FLATTEN_2 =
        """{"id":2,"attributes":{"value":{"level":42},"status":"pending"}}"""
    private const val JSON_RESILIENT_1 =
        """[{"id":"r1","value":10},{"id":"r2","value":"NOT_AN_INT"},{"id":"r3","value":30}]"""
    private const val JSON_RESILIENT_2 =
        """[{"id":"r4","value":null},{"id":"r5","value":99}]"""
    private const val JSON_DECODER_1 = """{"id":"c1","secret":"AABBCC","score":null}"""
    private const val JSON_DECODER_2 = """{"id":"c2","secret":"FF00FF","score":42}"""
    private const val JSON_POLY_FALLBACK_1 =
        """{"id":"f1","devices":[{"type":"NeverSeenBefore","payload":"xyz"},{"type":"Light","brightness":50}]}"""
    private const val JSON_POLY_FALLBACK_2 =
        """{"id":"f2","devices":[{"type":"AlienSensor"},{"type":"Thermostat","temperature":20.0}]}"""
    private const val JSON_OPAQUE_METADATA =
        """{"id":"bench-1","metadata":{"tags":["a","b"],"count":2}}"""
    private const val JSON_SSE_DEVICE_EVENT =
        """{"eventType":"DEVICE_EVENT","eventTime":42,"deviceEvent":{"deviceId":"abc"}}"""

    fun run(runs: Int, warmupIters: Int) {
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (threadBean == null || !threadBean.isThreadAllocatedMemorySupported) {
            println("  ⚠️  ThreadMXBean not available — skipping special features benchmark.")
            return
        }
        threadBean.isThreadAllocatedMemoryEnabled = true

        println("\n════════════════════════════════════════════════════════════════")
        println("  👻 GHOST SPECIAL FEATURES — EXCLUSIVE CAPABILITIES BENCHMARK")
        println("════════════════════════════════════════════════════════════════")
        println("  These features have NO equivalent in Gson, Moshi, KSer, or Jackson.")
        println("  Measured with $runs runs × ${warmupIters}-iteration JIT warmup.\n")

        benchmarkFeature(
            threadBean, runs, warmupIters,
            label = LABEL_SEALED,
            jsonSamples = listOf(JSON_SEALED_1, JSON_SEALED_2)
        ) { json -> Ghost.deserialize<SmartHome>(json) }

        benchmarkFeature(
            threadBean, runs, warmupIters,
            label = LABEL_FLATTEN,
            jsonSamples = listOf(JSON_FLATTEN_1, JSON_FLATTEN_2)
        ) { json -> Ghost.deserialize<FlattenedModel>(json) }

        benchmarkFeature(
            threadBean, runs, warmupIters,
            label = LABEL_RESILIENT,
            jsonSamples = listOf(JSON_RESILIENT_1, JSON_RESILIENT_2)
        ) { json -> Ghost.deserialize<List<ResilientItem>>(json) }

        benchmarkFeature(
            threadBean, runs, warmupIters,
            label = LABEL_DECODER,
            jsonSamples = listOf(JSON_DECODER_1, JSON_DECODER_2)
        ) { json -> Ghost.deserialize<CustomCoderStressModel>(json) }

        benchmarkFeature(
            threadBean, runs, warmupIters,
            label = LABEL_FALLBACK,
            jsonSamples = listOf(JSON_POLY_FALLBACK_1, JSON_POLY_FALLBACK_2)
        ) { json -> Ghost.deserialize<SmartHome>(json) }

        benchmarkBytesFeature(
            threadBean, runs, warmupIters,
            label = LABEL_RAWJSON_CAPTURE,
            jsonSamples = listOf(JSON_OPAQUE_METADATA)
        ) { bytes -> Ghost.deserialize<OpaqueMetadataEnvelope>(bytes) }

        val capturedMetadata = Ghost.deserialize<OpaqueMetadataEnvelope>(
            JSON_OPAQUE_METADATA.encodeToByteArray()
        ).metadata

        benchmarkAllocOnlyFeature(
            threadBean, runs, warmupIters,
            label = LABEL_RAWJSON_KIND,
        ) {
            capturedMetadata.kind()
        }

        benchmarkAllocOnlyFeature(
            threadBean, runs, warmupIters,
            label = LABEL_RAWJSON_DECODE_AS,
        ) {
            capturedMetadata.decodeAs<TagsProbe>()
        }

        benchmarkBytesFeature(
            threadBean, runs, warmupIters,
            label = LABEL_ENVELOPE_PAYLOAD,
            jsonSamples = listOf(JSON_SSE_DEVICE_EVENT)
        ) { bytes -> SseEventEnvelopeSerializer.parsePayload(bytes) }

        benchmarkBytesFeature(
            threadBean, runs, warmupIters,
            label = LABEL_ENVELOPE_TYPED,
            jsonSamples = listOf(JSON_SSE_DEVICE_EVENT)
        ) { bytes -> SseEventEnvelopeSerializer.parseTyped(bytes) }

        println("════════════════════════════════════════════════════════════════\n")
    }

    private inline fun <reified T> benchmarkFeature(
        threadBean: ThreadMXBean,
        runs: Int,
        warmupIters: Int,
        label: String,
        jsonSamples: List<String>,
        crossinline deserialize: (String) -> T
    ) {
        repeat(warmupIters) {
            for (sample in jsonSamples) {
                deserialize(sample)
            }
        }

        val threadId = Thread.currentThread().id
        var totalTimeNanos = 0L
        var totalAllocBytes = 0L
        val samplesPerRun = jsonSamples.size

        repeat(runs) {
            for (sample in jsonSamples) {
                val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
                val timeBefore = System.nanoTime()
                consume(deserialize(sample))
                totalTimeNanos += System.nanoTime() - timeBefore
                totalAllocBytes += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
            }
        }

        printResult(label, totalTimeNanos, totalAllocBytes, runs.toLong() * samplesPerRun)
    }

    private inline fun benchmarkBytesFeature(
        threadBean: ThreadMXBean,
        runs: Int,
        warmupIters: Int,
        label: String,
        jsonSamples: List<String>,
        crossinline block: (ByteArray) -> Any?
    ) {
        val payloads = jsonSamples.map { it.encodeToByteArray() }
        repeat(warmupIters) {
            for (payload in payloads) {
                block(payload)
            }
        }

        val threadId = Thread.currentThread().id
        var totalTimeNanos = 0L
        var totalAllocBytes = 0L
        val samplesPerRun = payloads.size

        repeat(runs) {
            for (payload in payloads) {
                val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
                val timeBefore = System.nanoTime()
                consume(block(payload))
                totalTimeNanos += System.nanoTime() - timeBefore
                totalAllocBytes += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
            }
        }

        printResult(label, totalTimeNanos, totalAllocBytes, runs.toLong() * samplesPerRun)
    }

    private inline fun benchmarkAllocOnlyFeature(
        threadBean: ThreadMXBean,
        runs: Int,
        warmupIters: Int,
        label: String,
        crossinline block: () -> Unit
    ) {
        repeat(warmupIters) { block() }
        val threadId = Thread.currentThread().id
        var totalTimeNanos = 0L
        var totalAllocBytes = 0L
        repeat(runs) {
            val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
            val timeBefore = System.nanoTime()
            block()
            totalTimeNanos += System.nanoTime() - timeBefore
            totalAllocBytes += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
        }
        printResult(label, totalTimeNanos, totalAllocBytes, runs.toLong())
    }

    private fun printResult(label: String, totalTimeNanos: Long, totalAllocBytes: Long, totalOps: Long) {
        val avgTimeUs = totalTimeNanos / (totalOps * 1000.0)
        val avgAllocBytes = totalAllocBytes / totalOps
        val totalAllocKb = totalAllocBytes / 1024.0
        println(
            "  %-58s │ %6.2f µs/op │ %5d B/op │ %8.1f KB total".format(
                label,
                avgTimeUs,
                avgAllocBytes,
                totalAllocKb
            )
        )
    }
}
