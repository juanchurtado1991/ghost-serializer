package com.ghost.serialization.sample.api

actual fun parseWithMoshi(bytes: ByteArray): BenchmarkResult = 
    BenchmarkResult(timeMs = -1.0, allocatedBytes = 0L, isSupported = false)
