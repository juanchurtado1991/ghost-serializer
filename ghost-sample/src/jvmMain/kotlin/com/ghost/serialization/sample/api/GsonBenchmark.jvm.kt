package com.ghost.serialization.sample.api

import com.ghost.serialization.sample.domain.CharacterResponse
import com.google.gson.Gson
import kotlin.time.TimeSource

private val gson = Gson()

actual fun parseWithGson(bytes: ByteArray): BenchmarkResult {
    return try {
        val jsonString = bytes.decodeToString()
        
        val startMem = getCurrentThreadAllocatedBytes()
        val start = TimeSource.Monotonic.markNow()
        
        gson.fromJson(jsonString, CharacterResponse::class.java)
        
        val end = TimeSource.Monotonic.markNow()
        val endMem = getCurrentThreadAllocatedBytes()
        
        BenchmarkResult(
            timeMs = (end - start).inWholeMicroseconds / 1000.0,
            allocatedBytes = if (startMem >= 0 && endMem >= 0) endMem - startMem else 0L
        )
    } catch (e: Exception) {
        BenchmarkResult(-1.0, 0L)
    }
}
