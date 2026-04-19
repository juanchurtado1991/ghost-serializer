package com.ghostserializer.sample.api

actual fun parseWithGson(bytes: ByteArray): BenchmarkResult {
    return BenchmarkResult(timeMs = -1.0, allocatedBytes = 0L, isSupported = false)
}
