package com.ghost.benchmark

import com.ghost.serialization.integration.model.BenchmarkMetrics

/**
 * Console table rendering for benchmark results — reads already-computed [ModeMetrics]/
 * [BenchmarkMetrics]/[EngineRank] data, prints formatted tables. Split out since this changes for
 * a different reason (table layout/formatting) than how the numbers are measured or reduced.
 */

/** Prints aggregated synthetic tables (latency, GB/s, allocation) for every workload. */
internal fun printFinalResults(finalResults: BenchmarkSessionResults, payloads: BenchmarkPayloads) {
    val sessions = BenchmarkStandard.SYNTHETIC_SESSIONS
    val samples = BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION
    val titleSuffix = " (STATISTICAL AVG OF $sessions SESSIONS × $samples SAMPLES)"

    printModeTables(
        title = "DESERIALIZATION: LIST_MEDIUM (200 objects)$titleSuffix",
        metrics = finalResults.listMedium,
        payloadBytes = payloads.listMediumBytes.size.toLong(),
    )
    printModeTables(
        title = "DESERIALIZATION: SYNC_FULL_LARGE (2000 objects)$titleSuffix",
        metrics = finalResults.syncLarge,
        payloadBytes = payloads.syncLargeBytes.size.toLong(),
    )
    printModeTables(
        title = "SERIALIZATION: WRITING (1000 objects)$titleSuffix",
        metrics = finalResults.writing,
        payloadBytes = payloads.writingBytes.size.toLong(),
    )
    printMicroLatencyTable(
        title = "STRESS TEST: DEEP NESTING (20 Levels)",
        subtitle = "Single-shot parse per engine after synthetic suite (632 B payload)",
        metrics = finalResults.stress.nesting,
    )
    printMicroLatencyTable(
        title = "FAILURE RESILIENCE (Malformed JSON)",
        subtitle = "Average of 100 failed parses per engine (2 581 B payload)",
        metrics = finalResults.failure,
    )
}

private fun printModeTables(title: String, metrics: ModeMetrics, payloadBytes: Long) {
    println("\n========================================================")
    println("BENCHMARK: $title")
    println("========================================================")
    println(
        "  Payload: %d bytes → µs/op and decimal GB/s (payload / seconds / 10⁹)".format(payloadBytes)
    )
    printRankedSubTable(label = "STRING MODE", metrics = metrics.string, payloadBytes = payloadBytes)
    printRankedSubTable(label = "BYTES MODE", metrics = metrics.bytes, payloadBytes = payloadBytes)
    printRankedSubTable(label = "STREAMING MODE", metrics = metrics.streaming, payloadBytes = payloadBytes)
}

private fun printRankedSubTable(label: String, metrics: BenchmarkMetrics, payloadBytes: Long) {
    println("\n--- $label ---")
    printRankedTableBody(metrics = metrics, payloadBytes = payloadBytes)
}

/**
 * Cold start is one-time init latency, not throughput — GB/s would obscure the startup
 * cost and make the result payload-size-dependent, so this stays in milliseconds.
 */
internal fun printColdStartTable(title: String, metrics: BenchmarkMetrics) {
    println("\n========================================================")
    println("BENCHMARK: $title")
    println("========================================================")

    val rankings = engineRankings(metrics = metrics).sortedBy { it.nanos }
    println("| RANK | ENGINE   | Latency (ms) |")
    println("|------|----------|--------------|")
    rankings.forEachIndexed { index, rank ->
        println(
            "| %-4d | %-8s | %12.3f |".format(
                index + 1,
                rank.name,
                rank.nanos / 1_000_000.0,
            )
        )
    }

    val winner = rankings.first()
    val slowest = rankings.last()
    val latencyReduction =
        ((slowest.nanos.toDouble() - winner.nanos.toDouble()) / slowest.nanos.toDouble()) * 100.0
    println(
        "   WINNER: ${winner.name} (%.1f%% lower latency than ${slowest.name})".format(
            latencyReduction
        )
    )
}

/**
 * Deep-nesting / malformed-JSON micro-benchmarks measure latency only (same GB/s rationale
 * as [printColdStartTable]); allocation isn't measured here.
 */
private fun printMicroLatencyTable(
    title: String,
    subtitle: String,
    metrics: BenchmarkMetrics,
) {
    println("\n========================================================")
    println("BENCHMARK: $title")
    println("========================================================")
    println("  $subtitle → latency only (µs/op)")

    val rankings = engineRankings(metrics = metrics).sortedBy { it.nanos }
    println("| RANK | ENGINE   | Latency (µs/op) |")
    println("|------|----------|-----------------|")
    rankings.forEachIndexed { index, rank ->
        println(
            "| %-4d | %-8s | %15.1f |".format(
                index + 1,
                rank.name,
                BenchmarkThroughput.nanosToMicros(nanosPerOp = rank.nanos),
            )
        )
    }

    val winner = rankings.first()
    val slowest = rankings.last()
    if (winner.nanos > 0 && slowest.nanos > 0) {
        val latencyReduction =
            ((slowest.nanos.toDouble() - winner.nanos.toDouble()) / slowest.nanos.toDouble()) * 100.0
        println(
            "   WINNER: ${winner.name} (%.1f%% lower latency than ${slowest.name})".format(
                latencyReduction
            )
        )
    }
}

private fun printRankedTableBody(metrics: BenchmarkMetrics, payloadBytes: Long) {
    val rankings = engineRankings(metrics = metrics)
        .sortedByDescending {
            BenchmarkThroughput.nanosToGbPerSec(nanosPerOp = it.nanos, payloadBytes = payloadBytes)
        }

    println("| RANK | ENGINE   | Throughput (GB/s) | Latency (µs/op)   | Mem (KB/op) |")
    println("|------|----------|-------------------|-------------------|------------|")

    rankings.forEachIndexed { index, rank ->
        val meanUs = BenchmarkThroughput.nanosToMicros(nanosPerOp = rank.nanos)
        val stdevUs = BenchmarkThroughput.nanosStdevToMicros(stdevNanos = rank.stDevNanos)
        val meanGb = BenchmarkThroughput.nanosToGbPerSec(nanosPerOp = rank.nanos, payloadBytes = payloadBytes)
        val stdevGb = BenchmarkThroughput.nanosStdevToGbPerSec(
            meanNanos = rank.nanos,
            stdevNanos = rank.stDevNanos,
            payloadBytes = payloadBytes,
        )
        val memKb = rank.mem / 1024.0
        val latencyStr = BenchmarkThroughput.formatMicrosWithStdev(mean = meanUs, stdev = stdevUs)
        val speedStr = BenchmarkThroughput.formatGbPerSecWithStdev(mean = meanGb, stdev = stdevGb)

        println(
            "| %-4d | %-8s | %-17s | %-17s | %10.1f |".format(
                index + 1,
                rank.name,
                speedStr,
                latencyStr,
                memKb,
            )
        )
    }

    val winner = rankings.first()
    val slowest = rankings.last()
    if (winner.nanos > 0 && slowest.nanos > 0) {
        val speedVsSlowest = ((slowest.nanos.toDouble() / winner.nanos.toDouble()) - 1.0) * 100.0
        val memSavedVsSlowest = if (slowest.mem > 0) {
            ((slowest.mem.toDouble() - winner.mem.toDouble()) / slowest.mem.toDouble()) * 100.0
        } else {
            0.0
        }
        val memString = if (memSavedVsSlowest >= 0.0) {
            "%.1f%% less memory".format(memSavedVsSlowest)
        } else {
            "but uses %.1f%% MORE memory".format(-memSavedVsSlowest)
        }
        println(
            "   WINNER: ${winner.name} (%.1f%% faster than ${slowest.name}, %s)".format(
                speedVsSlowest,
                memString
            )
        )
    }
}
