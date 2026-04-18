package com.ghost.serialization.sample.api

actual fun parseWithMoshi(bytes: ByteArray): BenchmarkResult {
    // Moshi is not supported on Wasm, returning empty result
    return BenchmarkResult(timeMs = 0.0, allocatedBytes = 0L)
}
