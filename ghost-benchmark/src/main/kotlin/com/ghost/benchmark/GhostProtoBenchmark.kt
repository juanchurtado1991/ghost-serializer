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
 * Exercises KSP-generated serializers via `GhostProto` on the `ProtoBenchUser` fixture
 * (quoted int64 strings, default-value omission on encode).
 */
object GhostProtoBenchmark {

    /** Proto3 JSON fixture — default fields omitted on wire. */
    private const val JSON_USER =
        """{"userId":"42","name":"Ghost Benchmark","email":"bench@ghost.io","score":88.5,"isActive":true,"role":"VIEWER"}"""

    private const val JSON_USER_MINIMAL =
        """{"userId":"7","name":"Neo","email":"neo@matrix.io","score":100.0}"""

    /**
     * Runs proto3 JSON decode, encode, and round-trip scenarios.
     *
     * @return `true` when the suite completes (always, including when ThreadMXBean is unavailable).
     */
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
            threadBean = threadBean,
            label = "Decode ProtoBenchUser (JSON bytes)",
            payload = JSON_USER,
        ) { bytes ->
            GhostProto.deserialize<ProtoBenchUser>(bytes)
        }

        measureString(
            threadBean = threadBean,
            label = "Decode ProtoBenchUser (JSON string)",
            payload = JSON_USER,
        ) { text ->
            GhostProto.deserialize<ProtoBenchUser>(text)
        }

        val user = GhostProto.deserialize<ProtoBenchUser>(JSON_USER)

        measureBytes(
            threadBean = threadBean,
            label = "Encode ProtoBenchUser (encodeToBytes)",
            payload = JSON_USER,
        ) {
            GhostProto.encodeToBytes(user)
        }

        measureString(
            threadBean = threadBean,
            label = "Encode ProtoBenchUser (encodeToString)",
            payload = JSON_USER,
        ) {
            GhostProto.encodeToString(user)
        }

        measureString(
            threadBean = threadBean,
            label = "Round-trip (decode → encodeToString, minimal profile)",
            payload = JSON_USER_MINIMAL,
        ) {
            val decoded = GhostProto.deserialize<ProtoBenchUser>(JSON_USER_MINIMAL)
            GhostProto.encodeToString(decoded)
        }

        println("════════════════════════════════════════════════════════════════\n")
        return true
    }
}
