package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.ComplexResponse
import okio.Buffer

private const val ROUNDS = 20

@OptIn(InternalGhostApi::class)
fun main(args: Array<String>) {
    val mode = args.firstOrNull() ?: "bytes"
    val userCount = args.getOrNull(1)?.toIntOrNull() ?: 200
    val warmup = if (userCount >= 2000) 2_000 else 20_000
    val perRound = if (userCount >= 2000) 200 else 2_000
    val complex = generateComplexData(userCount)
    val json = generateNeutralJson(complex)
    val bytes = json.encodeToByteArray()

    val decode: () -> ComplexResponse = when (mode) {
        "bytes" -> { -> Ghost.deserialize<ComplexResponse>(bytes) }
        "string" -> { -> Ghost.deserialize<ComplexResponse>(json) }
        "streaming" -> { -> Ghost.deserializeStreaming<ComplexResponse>(Buffer().write(bytes)) }
        else -> error("Unknown mode: $mode")
    }

    repeat(warmup) { decode() }

    val roundNanos = LongArray(ROUNDS)
    repeat(ROUNDS) { r ->
        val start = System.nanoTime()
        repeat(perRound) { decode() }
        roundNanos[r] = (System.nanoTime() - start) / perRound
    }
    roundNanos.sort()
    println("MICRO_RESULT_${mode.uppercase()} median_ns=${roundNanos[ROUNDS / 2]} min_ns=${roundNanos[0]}")
}
