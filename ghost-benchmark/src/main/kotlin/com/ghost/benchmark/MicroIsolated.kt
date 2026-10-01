package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.ComplexResponse
import okio.Buffer

private const val ROUNDS = 20

private const val MODE_BYTES = "bytes"
private const val MODE_STRING = "string"
private const val MODE_STREAMING = "streaming"
private const val MODE_WRITE_BYTES = "write-bytes"
private const val MODE_WRITE_STRING = "write-string"
private const val MODE_WRITE_STREAMING = "write-streaming"

private const val DEFAULT_USER_COUNT = 200
private const val LARGE_PAYLOAD_USER_COUNT = 2000

/** Large payloads need far fewer iterations per round to stay in the same wall-clock budget. */
private const val SMALL_PAYLOAD_WARMUP = 20_000
private const val LARGE_PAYLOAD_WARMUP = 2_000
private const val SMALL_PAYLOAD_PER_ROUND = 2_000
private const val LARGE_PAYLOAD_PER_ROUND = 200

@OptIn(InternalGhostApi::class)
fun main(args: Array<String>) {
    val mode = args.firstOrNull() ?: MODE_BYTES
    val userCount = args.getOrNull(1)?.toIntOrNull() ?: DEFAULT_USER_COUNT
    val isLargePayload = userCount >= LARGE_PAYLOAD_USER_COUNT
    val warmup = if (isLargePayload) LARGE_PAYLOAD_WARMUP else SMALL_PAYLOAD_WARMUP
    val perRound = if (isLargePayload) LARGE_PAYLOAD_PER_ROUND else SMALL_PAYLOAD_PER_ROUND
    val complex = generateComplexData(count = userCount)
    val json = generateNeutralJson(data = complex)
    val bytes = json.encodeToByteArray()

    val op: () -> Any = when (mode) {
        MODE_BYTES -> { -> Ghost.deserialize<ComplexResponse>(bytes) }
        MODE_STRING -> { -> Ghost.deserialize<ComplexResponse>(json) }
        MODE_STREAMING -> { -> Ghost.deserializeStreaming<ComplexResponse>(Buffer().write(bytes)) }
        MODE_WRITE_BYTES -> { -> Ghost.encodeToBytes(complex) }
        MODE_WRITE_STRING -> { -> Ghost.encodeToString(complex) }
        MODE_WRITE_STREAMING -> { -> Ghost.serialize(Buffer(), complex) }
        else -> error("Unknown mode: $mode")
    }

    repeat(warmup) { op() }

    val roundNanos = LongArray(ROUNDS)
    repeat(ROUNDS) { r ->
        val start = System.nanoTime()
        repeat(perRound) { op() }
        roundNanos[r] = (System.nanoTime() - start) / perRound
    }
    roundNanos.sort()
    println("MICRO_RESULT_${mode.uppercase()} median_ns=${roundNanos[ROUNDS / 2]} min_ns=${roundNanos[0]}")
}
