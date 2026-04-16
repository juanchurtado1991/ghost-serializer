package com.ghost.serialization.sample.api

import com.ghost.serialization.sample.domain.*
import kotlin.time.TimeSource

object BenchmarkUtils {
    
    suspend fun runStressTest(data: CharacterResponse): BenchmarkResult {
        val iterations = 100 // Industrial load to measure CPU impact
        
        // Benchmark Ghost
        val startMemG = getCurrentThreadAllocatedBytes()
        val startG = TimeSource.Monotonic.markNow()
        
        repeat(iterations) {
            val ghostJson = com.ghost.serialization.Ghost.serialize(data)
            com.ghost.serialization.Ghost.deserialize<CharacterResponse>(ghostJson)
        }
        
        val endG = TimeSource.Monotonic.markNow()
        val endMemG = getCurrentThreadAllocatedBytes()
        
        val ghostTime = (endG - startG).inWholeMicroseconds / 1000.0
        val ghostMem = if (startMemG >= 0 && endMemG >= 0) endMemG - startMemG else 0L

        // Benchmark Moshi (via bridge)
        // We'll use the same raw bytes for a fair comparison
        val ghostJson = com.ghost.serialization.Ghost.serialize(data)
        val rawBytes = ghostJson.encodeToByteArray()
        
        val startM = TimeSource.Monotonic.markNow()
        val startMemM = getCurrentThreadAllocatedBytes()
        
        repeat(iterations) {
            parseWithMoshi(rawBytes)
        }
        
        val endM = TimeSource.Monotonic.markNow()
        val endMemM = getCurrentThreadAllocatedBytes()
        
        val moshiTime = (endM - startM).inWholeMicroseconds / 1000.0
        val moshiMem = if (startMemM >= 0 && endMemM >= 0) endMemM - startMemM else 0L
        
        return BenchmarkResult(
            ghostTimeMs = ghostTime,
            ghostAllocatedBytes = ghostMem,
            moshiTimeMs = moshiTime,
            moshiAllocatedBytes = moshiMem
        )
    }
}
