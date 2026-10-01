package com.ghost.benchmark

import com.ghost.serialization.integration.model.BenchResult
import com.ghost.serialization.integration.model.BenchmarkMetrics
import com.sun.management.ThreadMXBean

/**
 * Low-level measurement plumbing: timing/allocation primitives with zero knowledge of what's
 * being benchmarked (Ghost/KSER/Moshi specifics are passed in as lambdas by callers in
 * [GhostBenchmark]). Split out since this changes for a different reason than the suite
 * orchestration does — tuning sample counts or allocation tracking here never touches which
 * workloads run.
 */

private const val FAILURE_MEASURE_SAMPLES = 100
private const val NANOSECONDS_IN_SECOND = 1_000_000_000.0
private const val NANOSECONDS_PER_MICROSECOND = 1_000.0
private const val BYTES_PER_KB = 1024.0
private const val THROUGHPUT_BATCHES = 10
private const val MIN_RUNS_FOR_BATCHING = 10

@Volatile
var blackHoleSink: Any? = null

/** Prevents the JVM from dead-code-eliminating benchmark results. */
fun consume(obj: Any?) {
    blackHoleSink = obj
}

internal inline fun measureAvgFailSpeed(block: () -> Unit): Long {
    val startTime = System.nanoTime()
    repeat(FAILURE_MEASURE_SAMPLES) { block() }
    return (System.nanoTime() - startTime) / FAILURE_MEASURE_SAMPLES
}

/** Warms up then measures [block] over a UTF-8 byte payload and prints one result row. */
internal inline fun measureBytes(
    threadBean: ThreadMXBean,
    label: String,
    payload: String,
    crossinline block: (ByteArray) -> Any?,
) {
    val bytes = payload.encodeToByteArray()
    repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) { block(bytes) }
    BenchmarkProgress.logStep(label = "Measure: $label")
    reportGhostOnlyRow(
        threadBean = threadBean,
        label = label,
        payloadBytes = bytes.size.toLong(),
        block = { block(bytes) }
    )
}

internal fun measureEnginesRotated(
    sessionIndex: Int,
    threadBean: ThreadMXBean,
    engines: List<Pair<String, () -> Any?>>,
): BenchmarkMetrics {
    val byName = engines.associate { it.first to it.second }
    val ghostBlock = byName.getValue("ghost")
    val kserBlock = byName.getValue("kser")
    val moshiBlock = byName.getValue("moshi")

    // Regression signal first — Ghost vs KSER back-to-back, no GC between them.
    val ghostKserOrder = if (sessionIndex % 2 == 0) {
        listOf("ghost" to ghostBlock, "kser" to kserBlock)
    } else {
        listOf("kser" to kserBlock, "ghost" to ghostBlock)
    }
    val ghostKserResults = linkedMapOf<String, BenchResult>()
    for ((name, block) in ghostKserOrder) {
        val (result, nanos, alloc) = measurePerfBatched(
            threadBean = threadBean,
            samples = BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION,
            block = block,
        )
        consume(obj = result)
        ghostKserResults[name] = BenchResult(nanos = nanos, allocBytes = alloc)
    }

    val (moshiResult, moshiNanos, moshiAlloc) = measurePerfBatched(
        threadBean = threadBean,
        samples = BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION,
        block = moshiBlock,
    )
    consume(obj = moshiResult)

    return BenchmarkMetrics(
        ghost = ghostKserResults.getValue("ghost"),
        kser = ghostKserResults.getValue("kser"),
        moshi = BenchResult(nanos = moshiNanos, allocBytes = moshiAlloc),
    )
}

/**
 * Throughput measurement for the Ghost-only suites (Twitter/YAML): runs [block] [runs] times in
 * batches and returns (ops/sec, batch stddev, KB allocated per op). [threadBean] may be null when
 * allocation tracking is unsupported.
 */
internal inline fun <T> measurePerf(
    threadBean: ThreadMXBean?,
    runs: Int,
    crossinline block: () -> T,
): Triple<Double, Double, Double> {
    val numBatches = if (runs >= MIN_RUNS_FOR_BATCHING) THROUGHPUT_BATCHES else 1
    val runsPerBatch = runs / numBatches

    val currentThreadId = Thread.currentThread().id
    val startAllocatedBytes = threadBean?.getThreadAllocatedBytes(currentThreadId) ?: 0L
    val startTime = System.nanoTime()

    val batchThroughputs = DoubleArray(numBatches)
    repeat(numBatches) { b ->
        val start = System.nanoTime()
        repeat(runsPerBatch) {
            val res = block()
            consume(obj = res)
        }
        val elapsed = System.nanoTime() - start
        batchThroughputs[b] = runsPerBatch / (elapsed.toDouble() / NANOSECONDS_IN_SECOND)
    }

    val elapsedNanos = System.nanoTime() - startTime
    val endAllocatedBytes = threadBean?.getThreadAllocatedBytes(currentThreadId) ?: 0L

    val avgThroughput = runs / (elapsedNanos.toDouble() / NANOSECONDS_IN_SECOND)

    val stdDev = if (numBatches > 1) {
        val mean = batchThroughputs.average()
        val variance = batchThroughputs.map { (it - mean) * (it - mean) }.sum() / (numBatches - 1)
        kotlin.math.sqrt(variance)
    } else {
        0.0
    }

    val allocatedBytes = endAllocatedBytes - startAllocatedBytes
    val kbPerOp = if (allocatedBytes > 0) (allocatedBytes.toDouble() / runs) / BYTES_PER_KB else 0.0

    return Triple(avgThroughput, stdDev, kbPerOp)
}

internal inline fun <T> measurePerfBatched(
    threadBean: ThreadMXBean,
    samples: Int,
    crossinline block: () -> T,
): Triple<T, Long, Long> {
    val currentThreadId = Thread.currentThread().id
    val startAllocatedBytes = threadBean.getThreadAllocatedBytes(currentThreadId)
    val startTimeNanos = System.nanoTime()
    var lastResult: T? = null
    repeat(samples) {
        lastResult = block()
    }
    consume(obj = lastResult)
    val endTimeNanos = System.nanoTime()
    val endAllocatedBytes = threadBean.getThreadAllocatedBytes(currentThreadId)
    val durationNanos = (endTimeNanos - startTimeNanos) / samples
    val allocatedBytes = (endAllocatedBytes - startAllocatedBytes) / samples
    @Suppress("UNCHECKED_CAST")
    return Triple(lastResult as T, durationNanos, allocatedBytes)
}

/** Warms up then measures [block] over a [String] payload and prints one result row. */
internal inline fun measureString(
    threadBean: ThreadMXBean,
    label: String,
    payload: String,
    crossinline block: (String) -> Any?,
) {
    repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) { block(payload) }
    BenchmarkProgress.logStep(label = "Measure: $label")
    reportGhostOnlyRow(
        threadBean = threadBean,
        label = label,
        payloadBytes = payload.encodeToByteArray().size.toLong(),
        block = { block(payload) },
    )
}

internal inline fun measureTimeNanos(block: () -> Unit): Long {
    val startTimeNanos = System.nanoTime()
    block()
    return System.nanoTime() - startTimeNanos
}

/** Measures average latency/allocation of [block] over `MEASUREMENT_RUNS` and prints GB/s, µs/op, KB/op. */
internal inline fun reportGhostOnlyRow(
    threadBean: ThreadMXBean,
    label: String,
    payloadBytes: Long,
    crossinline block: () -> Any?,
) {
    val threadId = Thread.currentThread().id
    var totalNanos = 0L
    var totalAlloc = 0L

    repeat(BenchmarkStandard.MEASUREMENT_RUNS) {
        val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
        val timeBefore = System.nanoTime()
        block()
        totalNanos += System.nanoTime() - timeBefore
        totalAlloc += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
    }

    val avgMicros = totalNanos / BenchmarkStandard.MEASUREMENT_RUNS / NANOSECONDS_PER_MICROSECOND
    val avgKb = (totalAlloc.toDouble() / BenchmarkStandard.MEASUREMENT_RUNS) / BYTES_PER_KB
    val gbPerSec = BenchmarkThroughput.microsToGbPerSec(microsPerOp = avgMicros, payloadBytes = payloadBytes)
    println(
        "  %-58s │ %6.3f GB/s │ %8.2f µs/op │ %8.3f KB/op".format(label, gbPerSec, avgMicros, avgKb)
    )
}
