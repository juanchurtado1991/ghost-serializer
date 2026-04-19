package com.ghost.serialization.sample.api

import kotlin.time.TimeSource

actual fun parseWithGson(jsonString: String): BenchmarkResult {
    // GSON is not native to WASM
    return BenchmarkResult(0.0, 0L, false)
}
