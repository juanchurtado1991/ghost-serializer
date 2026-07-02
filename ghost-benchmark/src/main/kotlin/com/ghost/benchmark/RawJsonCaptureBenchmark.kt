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
 * Benchmarks opaque JSON capture: [RawJson] slice path vs [ByteArray] copy path.
 */
object RawJsonCaptureBenchmark {

    private val smallObjectJson = buildEnvelopeJson(depth = 2, width = 3)
    private val largeObjectJson = buildEnvelopeJson(depth = 4, width = 8)

    fun run(runs: Int, warmupIters: Int) {
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (threadBean == null || !threadBean.isThreadAllocatedMemorySupported) {
            println("  ⚠️  ThreadMXBean not available — skipping RawJson capture benchmark.")
            return
        }
        threadBean.isThreadAllocatedMemoryEnabled = true

        println("\n════════════════════════════════════════════════════════════════")
        println("  👻 RAW JSON CAPTURE — SLICE vs BYTEARRAY BENCHMARK")
        println("════════════════════════════════════════════════════════════════")
        println("  Flat [ByteArray] reader only. $runs runs × ${warmupIters}-iteration warmup.\n")

        measure(
            threadBean, runs, warmupIters,
            label = "Decode RawJson field (small metadata, slice capture)",
            json = smallObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataEnvelope>(bytes)
        }

        measure(
            threadBean, runs, warmupIters,
            label = "Decode ByteArray field (small metadata, copy capture)",
            json = smallObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataByteEnvelope>(bytes)
        }

        measure(
            threadBean, runs, warmupIters,
            label = "Decode RawJson field (large nested metadata)",
            json = largeObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataEnvelope>(bytes)
        }

        measure(
            threadBean, runs, warmupIters,
            label = "Decode ByteArray field (large nested metadata)",
            json = largeObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataByteEnvelope>(bytes)
        }

        measure(
            threadBean, runs, warmupIters,
            label = "Encode RawJson payload (slice write)",
            json = """{"id":"bench-1","body":{"nested":true}}"""
        ) { bytes ->
            val model = Ghost.deserialize<RawJsonPayloadModel>(bytes)
            Ghost.serialize(model)
        }

        measure(
            threadBean, runs, warmupIters,
            label = "Round-trip RawJson top-level",
            json = largeObjectJson.substringAfter("\"metadata\":").removeSuffix("}")
        ) { bytes ->
            val value = Ghost.deserialize<RawJson>(bytes)
            Ghost.serialize(value)
        }

        println("════════════════════════════════════════════════════════════════\n")
    }

    private inline fun <T> measure(
        threadBean: ThreadMXBean,
        runs: Int,
        warmupIters: Int,
        label: String,
        json: String,
        crossinline block: (ByteArray) -> T
    ) {
        val payload = json.encodeToByteArray()
        repeat(warmupIters) { block(payload) }

        val threadId = Thread.currentThread().id
        var totalNanos = 0L
        var totalAlloc = 0L

        repeat(runs) {
            val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
            val timeBefore = System.nanoTime()
            block(payload)
            totalNanos += System.nanoTime() - timeBefore
            totalAlloc += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
        }

        val avgMicros = totalNanos / runs / 1_000.0
        val avgBytes = totalAlloc / runs
        println(
            "  %-52s  %8.2f µs/op   %8d B/op".format(
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
