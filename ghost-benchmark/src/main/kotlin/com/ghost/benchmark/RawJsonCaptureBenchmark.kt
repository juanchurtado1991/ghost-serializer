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
 * Benchmarks opaque JSON capture: `RawJson` slice path vs [ByteArray]
 * copy path across bytes and string channels.
 *
 * Scalar access, `RawJson.decodeAs`, and JsonEnvelope routing are measured
 * in [GhostSpecialFeaturesBenchmark].
 */
object RawJsonCaptureBenchmark {

    private val smallObjectJson = buildEnvelopeJson(depth = 2, width = 3)
    private val largeObjectJson = buildEnvelopeJson(depth = 4, width = 8)
    private val encodePayloadJson = """{"id":"bench-1","body":{"nested":true}}"""
    private val topLevelRawJson = largeObjectJson.substringAfter("\"metadata\":").removeSuffix("}")

    /** Runs decode, encode, and round-trip RawJson capture scenarios; prints a summary table. */
    fun run() {
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (threadBean == null || !threadBean.isThreadAllocatedMemorySupported) {
            println("  ⚠️  ThreadMXBean not available — skipping RawJson capture benchmark.")
            return
        }
        threadBean.isThreadAllocatedMemoryEnabled = true

        println("\n════════════════════════════════════════════════════════════════")
        println("  👻 RAW JSON CAPTURE — BYTES vs STRING CHANNELS")
        println("════════════════════════════════════════════════════════════════")

        println("  ── Decode (model field with opaque metadata) ──")

        measureBytes(
            threadBean = threadBean,
            label = "Decode RawJson field (bytes, small, slice capture)",
            payload = smallObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataEnvelope>(bytes)
        }

        measureString(
            threadBean = threadBean,
            label = "Decode RawJson field (string, small, owned capture)",
            payload = smallObjectJson
        ) { json ->
            Ghost.deserialize<OpaqueMetadataEnvelope>(json)
        }

        measureBytes(
            threadBean = threadBean,
            label = "Decode ByteArray field (bytes, small, copy capture)",
            payload = smallObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataByteEnvelope>(bytes)
        }

        measureBytes(
            threadBean = threadBean,
            label = "Decode RawJson field (bytes, large nested metadata)",
            payload = largeObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataEnvelope>(bytes)
        }

        measureString(
            threadBean = threadBean,
            label = "Decode RawJson field (string, large nested metadata)",
            payload = largeObjectJson
        ) { json ->
            Ghost.deserialize<OpaqueMetadataEnvelope>(json)
        }

        measureBytes(
            threadBean = threadBean,
            label = "Decode ByteArray field (bytes, large nested metadata)",
            payload = largeObjectJson
        ) { bytes ->
            Ghost.deserialize<OpaqueMetadataByteEnvelope>(bytes)
        }

        println("\n  ── Encode (RawJson payload model) ──")

        val encodeModel =
            Ghost.deserialize<RawJsonPayloadModel>(encodePayloadJson.encodeToByteArray())

        measureBytes(
            threadBean = threadBean,
            label = "Encode RawJson payload (encodeToBytes, slice write)",
            payload = encodePayloadJson
        ) {
            Ghost.encodeToBytes(encodeModel)
        }

        measureString(
            threadBean = threadBean,
            label = "Encode RawJson payload (encodeToString, UTF-8 decode path)",
            payload = encodePayloadJson
        ) {
            Ghost.encodeToString(encodeModel)
        }

        println("\n  ── Top-level RawJson round-trip ──")

        measureBytes(
            threadBean = threadBean,
            label = "Top-level RawJson decode (bytes)",
            payload = topLevelRawJson
        ) { bytes ->
            Ghost.deserialize<RawJson>(bytes)
        }

        measureString(
            threadBean = threadBean,
            label = "Top-level RawJson decode (string)",
            payload = topLevelRawJson
        ) { json ->
            Ghost.deserialize<RawJson>(json)
        }

        measureBytes(
            threadBean = threadBean,
            label = "Top-level RawJson round-trip (bytes in/out)",
            payload = topLevelRawJson
        ) { bytes ->
            val value = Ghost.deserialize<RawJson>(bytes)
            Ghost.encodeToBytes(value)
        }

        measureString(
            threadBean = threadBean,
            label = "Top-level RawJson round-trip (string in/out)",
            payload = topLevelRawJson
        ) { json ->
            val value = Ghost.deserialize<RawJson>(json)
            Ghost.encodeToString(value)
        }

        println("════════════════════════════════════════════════════════════════\n")
    }

    private fun buildEnvelopeJson(depth: Int, width: Int): String {
        fun nested(level: Int): String {
            if (level == 0) return "\"leaf\":true"
            val inner = buildString {
                append('{')
                repeat(width) { index ->
                    if (index > 0) append(',')
                    append("\"k$level$index\":{")
                    append(nested(level = level - 1))
                    append('}')
                }
                append('}')
            }
            return inner
        }

        return """{"id":"bench-1","metadata":${nested(depth)}}"""
    }
}
