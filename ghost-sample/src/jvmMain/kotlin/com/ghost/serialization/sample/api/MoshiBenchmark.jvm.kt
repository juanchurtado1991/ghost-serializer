package com.ghost.serialization.sample.api

import com.ghost.serialization.sample.domain.CharacterResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.Buffer
import kotlin.time.TimeSource

actual fun parseWithMoshi(bytes: ByteArray): BenchmarkResult {
    return try {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(CharacterResponse::class.java)
        
        val startMem = getCurrentThreadAllocatedBytes()
        val start = TimeSource.Monotonic.markNow()
        
        val buffer = Buffer().write(bytes)
        adapter.fromJson(buffer)
        
        val end = TimeSource.Monotonic.markNow()
        val endMem = getCurrentThreadAllocatedBytes()
        
        BenchmarkResult(
            ghostTimeMs = 0.0,
            ghostAllocatedBytes = 0L,
            moshiTimeMs = (end - start).inWholeMicroseconds / 1000.0,
            moshiAllocatedBytes = if (startMem >= 0 && endMem >= 0) endMem - startMem else 0L
        )
    } catch (e: Exception) {
        BenchmarkResult(0.0, 0L, -1.0, -1L)
    }
}
