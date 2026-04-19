package com.ghost.serialization.sample.api

import kotlin.time.TimeSource

actual fun parseWithMoshi(jsonString: String): BenchmarkResult {
    // Moshi is not native to WASM
    return BenchmarkResult(0.0, 0L, false)
}
