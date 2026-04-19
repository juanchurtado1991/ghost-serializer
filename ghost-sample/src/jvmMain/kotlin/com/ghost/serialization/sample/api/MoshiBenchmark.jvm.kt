package com.ghost.serialization.sample.api

import com.ghost.serialization.sample.domain.CharacterResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.time.TimeSource

private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

actual fun parseWithMoshi(jsonString: String): BenchmarkResult {
    val start = TimeSource.Monotonic.markNow()
    val startMem = getCurrentThreadAllocatedBytes()
    
    moshi.adapter(CharacterResponse::class.java).fromJson(jsonString)
    
    val end = TimeSource.Monotonic.markNow()
    val endMem = getCurrentThreadAllocatedBytes()
    
    val time = (end - start).inWholeMicroseconds / 1000.0
    val mem = if (startMem >= 0 && endMem >= 0) endMem - startMem else 0L
    
    return BenchmarkResult(time, mem)
}
