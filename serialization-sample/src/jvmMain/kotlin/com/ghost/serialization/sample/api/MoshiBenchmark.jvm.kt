package com.ghost.serialization.sample.api

import com.ghost.serialization.sample.domain.CharacterResponse
import com.ghost.serialization.sample.domain.CharacterStatus
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.Buffer
import kotlin.time.TimeSource

@Suppress("CheckResult")
actual fun parseWithMoshi(bytes: ByteArray): BenchmarkResult {
    return try {
        val moshi = Moshi.Builder()
            .add(object {
                @ToJson
                fun toJson(status: CharacterStatus): String = when(status) {
                    CharacterStatus.Alive -> "Alive"
                    CharacterStatus.Dead -> "Dead"
                    CharacterStatus.Unknown -> "unknown"
                }
                @FromJson
                fun fromJson(name: String): CharacterStatus = when(name) {
                    "Alive" -> CharacterStatus.Alive
                    "Dead" -> CharacterStatus.Dead
                    else -> CharacterStatus.Unknown
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
