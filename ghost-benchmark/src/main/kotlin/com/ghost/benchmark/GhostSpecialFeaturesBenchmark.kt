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
import com.ghost.serialization.proto.wkt.ProtoAny
import com.ghost.serialization.proto.wkt.ProtoBytesValue
import com.ghost.serialization.proto.wkt.ProtoDuration
import com.ghost.serialization.proto.wkt.ProtoStruct
import com.ghost.serialization.proto.wkt.ProtoStructSerializer
import com.ghost.serialization.proto.wkt.ProtoTimestamp
import com.ghost.serialization.proto.wkt.ProtoValue
import com.ghost.serialization.types.decodeAs
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/**
 * Ghost-only micro-benchmark for features that have no equivalent in other JSON libraries.
 *
 * Covers polymorphism, structural flattening, resilience, custom decoders, opaque `RawJson`
 * envelopes, and protobuf well-known types. These workloads are measured independently because
 * Moshi, KotlinX Serialization, and Jackson do not expose comparable capabilities.
 *
 * Uses the same [ThreadMXBean] allocation methodology as the main synthetic harness.
 */
object GhostSpecialFeaturesBenchmark {

    private const val LABEL_SEALED = "Polymorphism — Sealed Class Dispatch"
    private const val LABEL_FLATTEN = "Structural Flattening — @GhostFlatten (3 levels deep)"
    private const val LABEL_RESILIENT = "Resilience — @GhostResilient (type mismatch recovery)"
    private const val LABEL_DECODER = "Custom Decoders — @GhostDecoder (hex + nullable transform)"
    private const val LABEL_FALLBACK =
        "Polymorphic Fallback — @GhostFallback (unknown discriminator)"
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

    /** Runs every exclusive-capability benchmark and prints a summary table. */
    fun run() {
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (threadBean == null || !threadBean.isThreadAllocatedMemorySupported) {
            println("  ⚠️  ThreadMXBean not available — skipping special features benchmark.")
            return
        }
        threadBean.isThreadAllocatedMemoryEnabled = true

        println("\n════════════════════════════════════════════════════════════════")
        println("  👻 GHOST SPECIAL FEATURES — EXCLUSIVE CAPABILITIES BENCHMARK")
        println("════════════════════════════════════════════════════════════════")
        println("  These features have NO equivalent in Moshi, KSer, or Jackson.")

        benchmarkFeature(
            threadBean,
            label = LABEL_SEALED,
            jsonSamples = listOf(JSON_SEALED_1, JSON_SEALED_2)
        ) { json -> Ghost.deserialize<SmartHome>(json) }

        benchmarkFeature(
            threadBean,
            label = LABEL_FLATTEN,
            jsonSamples = listOf(JSON_FLATTEN_1, JSON_FLATTEN_2)
        ) { json -> Ghost.deserialize<FlattenedModel>(json) }

        benchmarkFeature(
            threadBean,
            label = LABEL_RESILIENT,
            jsonSamples = listOf(JSON_RESILIENT_1, JSON_RESILIENT_2)
        ) { json -> Ghost.deserialize<List<ResilientItem>>(json) }

        benchmarkFeature(
            threadBean,
            label = LABEL_DECODER,
            jsonSamples = listOf(JSON_DECODER_1, JSON_DECODER_2)
        ) { json -> Ghost.deserialize<CustomCoderStressModel>(json) }

        benchmarkFeature(
            threadBean,
            label = LABEL_FALLBACK,
            jsonSamples = listOf(JSON_POLY_FALLBACK_1, JSON_POLY_FALLBACK_2)
        ) { json -> Ghost.deserialize<SmartHome>(json) }

        benchmarkBytesFeature(
            threadBean,
            label = LABEL_RAWJSON_CAPTURE,
            jsonSamples = listOf(JSON_OPAQUE_METADATA)
        ) { bytes -> Ghost.deserialize<OpaqueMetadataEnvelope>(bytes) }

        val capturedMetadata = Ghost.deserialize<OpaqueMetadataEnvelope>(
            JSON_OPAQUE_METADATA.encodeToByteArray()
        ).metadata

        benchmarkAllocOnlyFeature(
            threadBean,
            label = LABEL_RAWJSON_KIND,
        ) {
            capturedMetadata.kind()
        }

        benchmarkAllocOnlyFeature(
            threadBean,
            label = LABEL_RAWJSON_DECODE_AS,
        ) {
            capturedMetadata.decodeAs<TagsProbe>()
        }

        benchmarkBytesFeature(
            threadBean,
            label = LABEL_ENVELOPE_PAYLOAD,
            jsonSamples = listOf(JSON_SSE_DEVICE_EVENT)
        ) { bytes -> SseEventEnvelopeSerializer.parsePayload(bytes) }

        benchmarkBytesFeature(
            threadBean,
            label = LABEL_ENVELOPE_TYPED,
            jsonSamples = listOf(JSON_SSE_DEVICE_EVENT)
        ) { bytes -> SseEventEnvelopeSerializer.parseTyped(bytes) }

        println("\n  🤖 GOOGLE PROTOBUF WELL-KNOWN TYPES BENCHMARK")
        println("  ──────────────────────────────────────────────────────────────")

        val jsonDuration1 = "\"-123.450000000s\""
        val jsonDuration2 = "\"123456.000000789s\""
        benchmarkFeature(
            threadBean,
            label = "Protobuf — Deserialize ProtoDuration",
            jsonSamples = listOf(jsonDuration1, jsonDuration2)
        ) { json -> Ghost.deserialize<ProtoDuration>(json) }

        val dur1 = ProtoDuration(123456L, 789)
        val dur2 = ProtoDuration(-123L, -450000000)
        benchmarkFeature(
            threadBean,
            label = "Protobuf — Serialize ProtoDuration",
            jsonSamples = listOf("")
        ) {
            Ghost.encodeToString(dur1)
            Ghost.encodeToString(dur2)
        }

        val jsonTimestamp1 = "\"2026-07-08T12:55:00.123456789Z\""
        benchmarkFeature(
            threadBean,
            label = "Protobuf — Deserialize ProtoTimestamp",
            jsonSamples = listOf(jsonTimestamp1)
        ) { json -> Ghost.deserialize<ProtoTimestamp>(json) }

        val ts1 = ProtoTimestamp(1783515300L, 123456789)
        benchmarkFeature(
            threadBean,
            label = "Protobuf — Serialize ProtoTimestamp",
            jsonSamples = listOf("")
        ) { Ghost.encodeToString(ts1) }

        val jsonStruct1 = """{"a":null,"b":123.45,"c":"hello","d":true,"e":{"x":1.0},"f":[2.0]}"""
        benchmarkFeature(
            threadBean,
            label = "Protobuf — Deserialize ProtoStruct",
            jsonSamples = listOf(jsonStruct1)
        ) { json -> Ghost.deserialize(ProtoStructSerializer, json) }

        val struct1: ProtoStruct = mapOf(
            "a" to ProtoValue.Null,
            "b" to ProtoValue.Number(123.45),
            "c" to ProtoValue.Str("hello"),
            "d" to ProtoValue.Bool(true),
            "e" to ProtoValue.Struct(mapOf("x" to ProtoValue.Number(1.0))),
            "f" to ProtoValue.List(listOf(ProtoValue.Number(2.0)))
        )
        benchmarkFeature(
            threadBean,
            label = "Protobuf — Serialize ProtoStruct",
            jsonSamples = listOf("")
        ) { Ghost.encodeToString(ProtoStructSerializer, struct1) }

        val jsonAny1 = """{"@type":"type.googleapis.com/google.protobuf.Duration","value":"123s"}"""
        benchmarkFeature(
            threadBean,
            label = "Protobuf — Deserialize ProtoAny",
            jsonSamples = listOf(jsonAny1)
        ) { json -> Ghost.deserialize<ProtoAny>(json) }

        val any1 = ProtoAny("type.googleapis.com/google.protobuf.Duration", ByteArray(0))
        benchmarkFeature(
            threadBean,
            label = "Protobuf — Serialize ProtoAny",
            jsonSamples = listOf("")
        ) { Ghost.encodeToString(any1) }

        val jsonBytes1 = "\"YWJjZA==\""
        benchmarkFeature(
            threadBean,
            label = "Protobuf — Deserialize ProtoBytesValue",
            jsonSamples = listOf(jsonBytes1)
        ) { json -> com.ghost.serialization.proto.GhostProto.deserialize<ProtoBytesValue>(json) }

        val bytesVal1 = ProtoBytesValue("abcd".encodeToByteArray())
        benchmarkFeature(
            threadBean,
            label = "Protobuf — Serialize ProtoBytesValue",
            jsonSamples = listOf("")
        ) { Ghost.encodeToString(bytesVal1) }

        JsonPathTrackerBenchmark.run()

        println("════════════════════════════════════════════════════════════════\n")
    }

    private inline fun <reified T> benchmarkFeature(
        threadBean: ThreadMXBean,
        label: String,
        jsonSamples: List<String>,
        crossinline deserialize: (String) -> T
    ) {
        repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) {
            for (sample in jsonSamples) {
                deserialize(sample)
            }
        }

        BenchmarkProgress.logStep("Measure: $label")

        val threadId = Thread.currentThread().id
        var totalTimeNanos = 0L
        var totalAllocBytes = 0L
        val samplesPerRun = jsonSamples.size

        repeat(BenchmarkStandard.MEASUREMENT_RUNS) {
            for (sample in jsonSamples) {
                val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
                val timeBefore = System.nanoTime()
                consume(deserialize(sample))
                totalTimeNanos += System.nanoTime() - timeBefore
                totalAllocBytes += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
            }
        }

        printResult(
            label,
            totalTimeNanos,
            totalAllocBytes,
            BenchmarkStandard.MEASUREMENT_RUNS.toLong() * samplesPerRun,
            payloadBytes = jsonSamples
                .map { it.encodeToByteArray().size.toLong() }
                .average()
                .toLong()
                .coerceAtLeast(0L),
        )
    }

    private inline fun benchmarkBytesFeature(
        threadBean: ThreadMXBean,
        label: String,
        jsonSamples: List<String>,
        crossinline block: (ByteArray) -> Any?
    ) {
        val payloads = jsonSamples.map { it.encodeToByteArray() }
        repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) {
            for (payload in payloads) {
                block(payload)
            }
        }

        BenchmarkProgress.logStep("Measure: $label")

        val threadId = Thread.currentThread().id
        var totalTimeNanos = 0L
        var totalAllocBytes = 0L
        val samplesPerRun = payloads.size

        repeat(BenchmarkStandard.MEASUREMENT_RUNS) {
            for (payload in payloads) {
                val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
                val timeBefore = System.nanoTime()
                consume(block(payload))
                totalTimeNanos += System.nanoTime() - timeBefore
                totalAllocBytes += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
            }
        }

        printResult(
            label,
            totalTimeNanos,
            totalAllocBytes,
            BenchmarkStandard.MEASUREMENT_RUNS.toLong() * samplesPerRun,
            payloadBytes = payloads.map { it.size.toLong() }.average().toLong().coerceAtLeast(0L),
        )
    }

    private inline fun benchmarkAllocOnlyFeature(
        threadBean: ThreadMXBean,
        label: String,
        crossinline block: () -> Unit
    ) {
        repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) { block() }
        BenchmarkProgress.logStep("Measure: $label")
        val threadId = Thread.currentThread().id
        var totalTimeNanos = 0L
        var totalAllocBytes = 0L
        repeat(BenchmarkStandard.MEASUREMENT_RUNS) {
            val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
            val timeBefore = System.nanoTime()
            block()
            totalTimeNanos += System.nanoTime() - timeBefore
            totalAllocBytes += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
        }
        printResult(
            label,
            totalTimeNanos,
            totalAllocBytes,
            BenchmarkStandard.MEASUREMENT_RUNS.toLong(),
            payloadBytes = 0L
        )
    }

    private fun printResult(
        label: String,
        totalTimeNanos: Long,
        totalAllocBytes: Long,
        totalOps: Long,
        payloadBytes: Long,
    ) {
        val avgTimeUs = totalTimeNanos / (totalOps * 1000.0)
        val avgAllocKb = (totalAllocBytes.toDouble() / totalOps) / 1024.0
        val gbPerSec = if (payloadBytes > 0L) {
            BenchmarkThroughput.microsToGbPerSec(avgTimeUs, payloadBytes)
        } else {
            0.0
        }
        if (payloadBytes > 0L) {
            println(
                "  %-58s │ %6.3f GB/s │ %7.2f µs/op │ %8.3f KB/op".format(
                    label,
                    gbPerSec,
                    avgTimeUs,
                    avgAllocKb,
                )
            )
        } else {
            println(
                "  %-58s │      — GB/s │ %7.2f µs/op │ %8.3f KB/op".format(
                    label,
                    avgTimeUs,
                    avgAllocKb,
                )
            )
        }
    }
}
