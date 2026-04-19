package com.ghost.serialization.sample.api

import com.ghost.serialization.sample.domain.CharacterResponse
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun parseWithKSer(bytes: ByteArray): BenchmarkResult {
    val start = TimeSource.Monotonic.markNow()
    val startMem = getCurrentThreadAllocatedBytes()
    
    val jsonString = bytes.decodeToString()
    json.decodeFromString<CharacterResponse>(jsonString)
    
    val end = TimeSource.Monotonic.markNow()
    val endMem = getCurrentThreadAllocatedBytes()
    
    val time = (end - start).inWholeMicroseconds / 1000.0
    val mem = if (startMem >= 0 && endMem >= 0) endMem - startMem else 0L
    
    return BenchmarkResult(time, mem)
}
