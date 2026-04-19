package com.ghostserializer.sample.api

import com.ghostserializer.sample.domain.CharacterResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.Buffer
import kotlin.time.TimeSource

actual fun parseWithMoshi(bytes: ByteArray): BenchmarkResult {
    return try {
        val moshi = Moshi.Builder()
            .add(object {
                @com.squareup.moshi.ToJson fun toJson(status: com.ghostserializer.sample.domain.CharacterStatus): String = when(status) {
                    com.ghostserializer.sample.domain.CharacterStatus.Alive -> "Alive"
                    com.ghostserializer.sample.domain.CharacterStatus.Dead -> "Dead"
                    com.ghostserializer.sample.domain.CharacterStatus.Unknown -> "unknown"
                }
                @com.squareup.moshi.FromJson fun fromJson(name: String): com.ghostserializer.sample.domain.CharacterStatus = when(name) {
                    "Alive" -> com.ghostserializer.sample.domain.CharacterStatus.Alive
                    "Dead" -> com.ghostserializer.sample.domain.CharacterStatus.Dead
                    else -> com.ghostserializer.sample.domain.CharacterStatus.Unknown
                }
            })
            .addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(CharacterResponse::class.java)
        
        val startMem = getCurrentThreadAllocatedBytes()
        val start = TimeSource.Monotonic.markNow()
        
        val buffer = Buffer().write(bytes)
        adapter.fromJson(buffer)
        
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
