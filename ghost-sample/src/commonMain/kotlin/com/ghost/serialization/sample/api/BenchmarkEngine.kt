package com.ghost.serialization.sample.api

import com.ghost.serialization.sample.model.EngineResult
import com.ghost.serialization.sample.util.forceGC
import com.ghost.serialization.sample.util.getCurrentThreadAllocatedBytes
import kotlin.time.TimeSource

object BenchmarkEngine {

    const val WARMUP_ITERATIONS = 200
    const val BENCHMARK_ITERATIONS = 100
    const val NANOS_PER_MILLI = 1_000_000.0

    /**
     * Executes a benchmark with strict GC leveling and thread-local memory tracking.
     * 
     * @param name Name of the engine/test
     * @param onStatusChange Callback to report the UI status
     * @param iterations Number of iterations (defaults to BENCHMARK_ITERATIONS)
     * @param block The suspend function to execute
     */
    suspend fun measure(
        name: String,
        onStatusChange: (String) -> Unit,
        iterations: Int = BENCHMARK_ITERATIONS,
        block: suspend () -> Unit
    ): EngineResult {
        onStatusChange("Benchmarking $name...")
        forceGC()

        var totalTimeNs = 0L
        var totalMemoryBytes = 0L
        repeat(iterations) {
            val memStart = getCurrentThreadAllocatedBytes()
            val mark = TimeSource.Monotonic.markNow()

            block()

            val durationNs = mark.elapsedNow().inWholeNanoseconds
            val memEnd = getCurrentThreadAllocatedBytes()

            totalTimeNs += durationNs
            totalMemoryBytes += if (memEnd >= memStart) memEnd - memStart else 0L
        }

        return EngineResult(
            name = name,
            timeMs = (totalTimeNs / iterations.toDouble()) / NANOS_PER_MILLI,
            memoryBytes = totalMemoryBytes / iterations
        )
    }
}
