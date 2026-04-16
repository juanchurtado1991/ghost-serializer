package com.ghost.serialization.sample.api

import com.ghost.serialization.sample.domain.CharacterResponse
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

        return BenchmarkResult(
            timeMs = ghostTime,
            allocatedBytes = ghostMem
        )
    }
}
