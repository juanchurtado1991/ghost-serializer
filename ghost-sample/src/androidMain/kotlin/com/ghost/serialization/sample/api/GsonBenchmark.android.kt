package com.ghost.serialization.sample.api

import com.ghost.serialization.sample.domain.CharacterResponse
import com.google.gson.Gson
import kotlin.time.TimeSource

private val gson = Gson()

actual fun parseWithGson(jsonString: String): BenchmarkResult {
    val start = TimeSource.Monotonic.markNow()
    val startMem = getCurrentThreadAllocatedBytes()
    
    gson.fromJson(jsonString, CharacterResponse::class.java)
    
    val end = TimeSource.Monotonic.markNow()
    val endMem = getCurrentThreadAllocatedBytes()
    
    val time = (end - start).inWholeMicroseconds / 1000.0
    val mem = if (startMem >= 0 && endMem >= 0) endMem - startMem else 0L
    
    return BenchmarkResult(time, mem)
}
