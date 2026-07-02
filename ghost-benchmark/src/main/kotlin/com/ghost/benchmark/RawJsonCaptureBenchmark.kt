@file:OptIn(InternalGhostApi::class)

package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.OpaqueMetadataByteEnvelope
import com.ghost.serialization.integration.model.OpaqueMetadataEnvelope
import com.ghost.serialization.integration.model.RawJsonPayloadModel
import com.ghost.serialization.types.RawJson
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/**
 * Benchmarks opaque JSON capture: [RawJson] slice path vs [ByteArray] copy path,
 * across flat bytes and native string channels.
 */
object RawJsonCaptureBenchmark {

    private val smallObjectJson = buildEnvelopeJson(depth = 2, width = 3)
    private val largeObjectJson = buildEnvelopeJson(depth = 4, width = 8)
    private val encodePayloadJson = """{"id":"bench-1","body":{"nested":true}}"""
    private val topLevelRawJson = largeObjectJson.substringAfter("\"metadata\":").removeSuffix("}")

    fun run(runs: Int, warmupIters: Int) {
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (threadBean == null || !threadBean.isThreadAllocatedMemorySupported) {
            println("  ⚠️  ThreadMXBean not available — skipping RawJson capture benchmark.")
            return
        }
        threadBean.isThreadAllocatedMemoryEnabled = true

        println("\n════════════════════════════════════════════════════════════════")
        println("  👻 RAW JSON CAPTURE — BYTES vs STRING CHANNELS")
        println("════════════════════════════════════════════════════════════════")
        println("  $runs runs × ${warmupIters}-iteration warmup.\n")

        println("  ── Decode (model field with opaque metadata) ──")

        measureBytes(
            threadBean, runs, warmupIters,
            label = "Decode RawJson field (bytes, small, slice capture)",
            json = smallObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataEnvelope>(bytes)
        }

        measureString(
            threadBean, runs, warmupIters,
            label = "Decode RawJson field (string, small, owned capture)",
            json = smallObjectJson
        ) { json ->
            Ghost.deserialize<OpaqueMetadataEnvelope>(json)
        }

        measureBytes(
            threadBean, runs, warmupIters,
            label = "Decode ByteArray field (bytes, small, copy capture)",
            json = smallObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataByteEnvelope>(bytes)
        }

        measureBytes(
            threadBean, runs, warmupIters,
            label = "Decode RawJson field (bytes, large nested metadata)",
            json = largeObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataEnvelope>(bytes)
        }

        measureString(
            threadBean, runs, warmupIters,
            label = "Decode RawJson field (string, large nested metadata)",
            json = largeObjectJson
        ) { json ->
            Ghost.deserialize<OpaqueMetadataEnvelope>(json)
        }

        measureBytes(
            threadBean, runs, warmupIters,
            label = "Decode ByteArray field (bytes, large nested metadata)",
            json = largeObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataByteEnvelope>(bytes)
        }

        println("\n  ── Encode (RawJson payload model) ──")

        val encodeModel = Ghost.deserialize<RawJsonPayloadModel>(encodePayloadJson.encodeToByteArray())

        measureBytes(
            threadBean, runs, warmupIters,
            label = "Encode RawJson payload (encodeToBytes, slice write)",
            json = encodePayloadJson
        ) {
            Ghost.encodeToBytes(encodeModel)
        }

        measureString(
            threadBean, runs, warmupIters,
            label = "Encode RawJson payload (encodeToString, UTF-8 decode path)",
            json = encodePayloadJson
        ) {
            Ghost.encodeToString(encodeModel)
        }

        println("\n  ── Top-level RawJson round-trip ──")

        measureBytes(
            threadBean, runs, warmupIters,
            label = "Top-level RawJson decode (bytes)",
            json = topLevelRawJson
        ) { bytes ->
            Ghost.deserialize<RawJson>(bytes)
        }

        measureString(
            threadBean, runs, warmupIters,
            label = "Top-level RawJson decode (string)",
            json = topLevelRawJson
        ) { json ->
            Ghost.deserialize<RawJson>(json)
        }

        measureBytes(
            threadBean, runs, warmupIters,
            label = "Top-level RawJson round-trip (bytes in/out)",
            json = topLevelRawJson
        ) { bytes ->
            val value = Ghost.deserialize<RawJson>(bytes)
            Ghost.encodeToBytes(value)
        }

        measureString(
            threadBean, runs, warmupIters,
            label = "Top-level RawJson round-trip (string in/out)",
            json = topLevelRawJson
        ) { json ->
            val value = Ghost.deserialize<RawJson>(json)
            Ghost.encodeToString(value)
        }

        println("\n  ── RawJson scalar access (captured metadata slice) ──")

        val accessEnvelopeBytes = smallObjectJson.encodeToByteArray()
        val accessRaw = Ghost.deserialize<OpaqueMetadataEnvelope>(accessEnvelopeBytes).metadata

        measureAllocOnly(
            threadBean, runs, warmupIters,
            label = "RawJson.kind() on captured slice",
        ) {
            accessRaw.kind()
        }

        measureAllocOnly(
            threadBean, runs, warmupIters,
            label = "RawJson.asDisplayString() on captured slice",
        ) {
            accessRaw.asDisplayString()
        }

        println("\n  ── JsonEnvelope routing (SSE-style fat envelope) ──")

        val sseJson = """
            {"eventType":"DEVICE_EVENT","eventTime":42,"deviceEvent":{"deviceId":"abc"}}
        """.trimIndent()

        measureBytes(
            threadBean, runs, warmupIters,
            label = "SseEventEnvelope.parsePayload (bytes)",
            json = sseJson
        ) { bytes ->
            com.ghost.serialization.integration.model.SseEventEnvelopeSerializer.parsePayload(bytes)
        }

        measureBytes(
            threadBean, runs, warmupIters,
            label = "SseEventEnvelope.parseTyped (bytes)",
            json = sseJson
        ) { bytes ->
            com.ghost.serialization.integration.model.SseEventEnvelopeSerializer.parseTyped(bytes)
        }

        println("════════════════════════════════════════════════════════════════\n")
    }

    private inline fun measureAllocOnly(
        threadBean: ThreadMXBean,
        runs: Int,
        warmupIters: Int,
        label: String,
        crossinline block: () -> Unit,
    ) {
        repeat(warmupIters) { block() }
        report(threadBean, runs, label, block = block)
    }

    private inline fun measureBytes(
        threadBean: ThreadMXBean,
        runs: Int,
        warmupIters: Int,
        label: String,
        json: String,
        crossinline block: (ByteArray) -> Any?
    ) {
        val payload = json.encodeToByteArray()
        repeat(warmupIters) { block(payload) }
        report(threadBean, runs, label, block = { block(payload) })
    }

    private inline fun measureString(
        threadBean: ThreadMXBean,
        runs: Int,
        warmupIters: Int,
        label: String,
        json: String,
        crossinline block: (String) -> Any?
    ) {
        repeat(warmupIters) { block(json) }
        report(threadBean, runs, label, block = { block(json) })
    }

    private inline fun report(
        threadBean: ThreadMXBean,
        runs: Int,
        label: String,
        crossinline block: () -> Any?
    ) {
        val threadId = Thread.currentThread().id
        var totalNanos = 0L
        var totalAlloc = 0L

        repeat(runs) {
            val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
            val timeBefore = System.nanoTime()
            block()
            totalNanos += System.nanoTime() - timeBefore
            totalAlloc += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
        }

        val avgMicros = totalNanos / runs / 1_000.0
        val avgBytes = totalAlloc / runs
        println(
            "  %-58s  %8.2f µs/op   %8d B/op".format(
                label,
                avgMicros,
                avgBytes
            )
        )
    }

    private fun buildEnvelopeJson(depth: Int, width: Int): String {
        fun nested(level: Int): String {
            if (level == 0) return "\"leaf\":true"
            val inner = buildString {
                append('{')
                repeat(width) { index ->
                    if (index > 0) append(',')
                    append("\"k$level$index\":{")
                    append(nested(level - 1))
                    append('}')
                }
                append('}')
            }
            return inner
        }

        return """{"id":"bench-1","metadata":${nested(depth)}}"""
    }
}
