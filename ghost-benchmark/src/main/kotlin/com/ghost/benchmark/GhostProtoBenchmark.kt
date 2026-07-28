@file:OptIn(InternalGhostApi::class)

package com.ghost.benchmark

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.ProtoBenchUser
import com.ghost.serialization.proto.GhostProto
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/**
 * Ghost-only proto3 JSON round-trip benchmark (no KSER/Moshi equivalent).
 *
 * Exercises KSP-generated serializers with [GhostProtoJsonFlatReader] via [GhostProto] on the
 * integration [ProtoBenchUser] fixture (quoted int64, default-value omission on encode).
 */
object GhostProtoBenchmark {

    /** Proto3 JSON — default fields omitted on wire. */
    private const val JSON_USER =
        """{"userId":"42","name":"Ghost Benchmark","email":"bench@ghost.io","score":88.5,"isActive":true,"role":"VIEWER"}"""

    private const val JSON_USER_MINIMAL =
        """{"userId":"7","name":"Neo","email":"neo@matrix.io","score":100.0}"""

    fun run(): Boolean {
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (threadBean == null || !threadBean.isThreadAllocatedMemorySupported) {
            println("  ⚠️  ThreadMXBean not available — skipping Proto3 JSON benchmark.")
            return true
        }
        threadBean.isThreadAllocatedMemoryEnabled = true

        println("\n════════════════════════════════════════════════════════════════")
        println("  👻 PROTO3 JSON ROUND-TRIP — GhostProto (ProtoBenchUser)")
        println("════════════════════════════════════════════════════════════════")

        measureBytes(
            threadBean,
            label = "Decode ProtoBenchUser (JSON bytes)",
            json = JSON_USER,
        ) { bytes ->
            GhostProto.deserialize<ProtoBenchUser>(bytes)
        }

        measureString(
            threadBean,
            label = "Decode ProtoBenchUser (JSON string)",
            json = JSON_USER,
        ) { text ->
            GhostProto.deserialize<ProtoBenchUser>(text)
        }

        val user = GhostProto.deserialize<ProtoBenchUser>(JSON_USER)

        measureBytes(
            threadBean,
            label = "Encode ProtoBenchUser (encodeToBytes)",
            json = JSON_USER,
        ) {
            GhostProto.encodeToBytes(user)
        }

        measureString(
            threadBean,
            label = "Encode ProtoBenchUser (encodeToString)",
            json = JSON_USER,
        ) {
            GhostProto.encodeToString(user)
        }

        measureString(
            threadBean,
            label = "Round-trip (decode → encodeToString, minimal profile)",
            json = JSON_USER_MINIMAL,
        ) {
            val decoded = GhostProto.deserialize<ProtoBenchUser>(JSON_USER_MINIMAL)
            GhostProto.encodeToString(decoded)
        }

        println("════════════════════════════════════════════════════════════════\n")
        return true
    }

    private inline fun measureBytes(
        threadBean: ThreadMXBean,
        label: String,
        json: String,
        crossinline block: (ByteArray) -> Any?,
    ) {
        val payload = json.encodeToByteArray()
        repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) { block(payload) }
        BenchmarkProgress.logStep("Measure: $label")
        report(threadBean, label, payloadBytes = payload.size.toLong(), block = { block(payload) })
    }

    private inline fun measureString(
        threadBean: ThreadMXBean,
        label: String,
        json: String,
        crossinline block: (String) -> Any?,
    ) {
        repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) { block(json) }
        BenchmarkProgress.logStep("Measure: $label")
        report(
            threadBean,
            label,
            payloadBytes = json.encodeToByteArray().size.toLong(),
            block = { block(json) },
        )
    }

    private inline fun report(
        threadBean: ThreadMXBean,
        label: String,
        payloadBytes: Long,
        crossinline block: () -> Any?,
    ) {
        val threadId = Thread.currentThread().id
        var totalNanos = 0L
        var totalAlloc = 0L

        repeat(BenchmarkStandard.MEASUREMENT_RUNS) {
            val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
            val timeBefore = System.nanoTime()
            block()
            totalNanos += System.nanoTime() - timeBefore
            totalAlloc += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
        }

        val avgMicros = totalNanos / BenchmarkStandard.MEASUREMENT_RUNS / 1_000.0
        val avgKb = (totalAlloc.toDouble() / BenchmarkStandard.MEASUREMENT_RUNS) / 1024.0
        val gbPerSec = BenchmarkThroughput.microsToGbPerSec(avgMicros, payloadBytes)
        println(
            "  %-58s │ %6.3f GB/s │ %8.2f µs/op │ %8.3f KB/op".format(
                label,
                gbPerSec,
                avgMicros,
                avgKb,
            )
        )
    }
}
