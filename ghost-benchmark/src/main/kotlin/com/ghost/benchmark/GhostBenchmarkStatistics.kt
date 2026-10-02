package com.ghost.benchmark

import com.ghost.serialization.integration.model.BenchResult
import com.ghost.serialization.integration.model.BenchmarkMetrics
import kotlin.math.sqrt

/**
 * Pure statistical reduction over already-measured [BenchmarkMetrics]/[ModeMetrics] — averaging
 * sessions, computing medians, and ranking engines. Split out since this changes for a different
 * reason than how the numbers are gathered (`GhostBenchmarkMeasurement.kt`) or printed
 * (`GhostBenchmarkPresentation.kt`) does.
 */

internal fun averageModeMetrics(list: List<ModeMetrics>): ModeMetrics {
    return ModeMetrics(
        string = averageMetrics(list = list.map { it.string }),
        bytes = averageMetrics(list = list.map { it.bytes }),
        streaming = averageMetrics(list = list.map { it.streaming })
    )
}

private fun averageBenchResult(list: List<BenchResult>): BenchResult {
    val avgNanos = list.map { it.nanos }.average().toLong()
    val avgBytes = list.map { it.allocBytes }.average().toLong()

    val stDevNanos = if (list.size > 1) {
        val avg = avgNanos / 1_000_000.0
        val variance = list.map { (it.nanos / 1_000_000.0 - avg).let { d -> d * d } }.average()
        (sqrt(variance) * 1_000_000.0).toLong()
    } else 0L

    return BenchResult(nanos = avgNanos, allocBytes = avgBytes, stdevNanos = stDevNanos)
}

private fun averageMetrics(list: List<BenchmarkMetrics>): BenchmarkMetrics {
    return BenchmarkMetrics(
        ghost = averageBenchResult(list = list.map { it.ghost }),
        kser = averageBenchResult(list = list.map { it.kser }),
        moshi = averageBenchResult(list = list.map { it.moshi }),
    )
}

private fun median(values: List<Double>): Double {
    if (values.isEmpty()) {
        return 0.0
    }
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2.0
    } else {
        sorted[mid]
    }
}

internal fun engineRankings(metrics: BenchmarkMetrics): List<EngineRank> {
    return listOf(
        EngineRank(
            name = "GHOST",
            nanos = metrics.ghost.nanos,
            mem = metrics.ghost.allocBytes,
            stDevNanos = metrics.ghost.stdevNanos
        ),
        EngineRank(
            name = "KSER",
            nanos = metrics.kser.nanos,
            mem = metrics.kser.allocBytes,
            stDevNanos = metrics.kser.stdevNanos
        ),
        EngineRank(
            name = "MOSHI",
            nanos = metrics.moshi.nanos,
            mem = metrics.moshi.allocBytes,
            stDevNanos = metrics.moshi.stdevNanos
        ),
    ).filter { it.nanos > 0L }
}

/**
 * Maps per-session synthetic measurements into calculator observations.
 *
 * Speed uses the median of per-session Ghost-vs-KSER ratios (robust to outliers); Ghost and
 * KSER are measured back-to-back per session (see `measureEnginesRotated`). Encoded as
 * ghost=1.0, kser=median(kser_i/ghost_i) for [RegressionCalculator.Metric.LATENCY].
 */
internal fun syntheticObservations(run: SyntheticRunResults): List<RegressionCalculator.Observed> {
    fun row(
        group: String,
        mode: String,
        sessions: List<ModeMetrics>,
        selector: (ModeMetrics) -> BenchmarkMetrics,
    ): RegressionCalculator.Observed {
        val perSession = sessions.map(selector)
        val advantages = perSession.mapNotNull { metrics ->
            val ghostMs = metrics.ghost.nanos / 1_000_000.0
            val kserMs = metrics.kser.nanos / 1_000_000.0
            if (ghostMs <= 0.0) {
                null
            } else {
                kserMs / ghostMs
            }
        }
        val medianAdvantage = median(values = advantages)
        return RegressionCalculator.Observed(
            group = group,
            category = mode,
            metric = RegressionCalculator.Metric.LATENCY,
            ghostSpeed = 1.0,
            kserSpeed = medianAdvantage,
            ghostMemKb = perSession.map { it.ghost.allocBytes / 1024.0 }.average(),
            kserMemKb = perSession.map { it.kser.allocBytes / 1024.0 }.average(),
        )
    }
    return listOf(
        row(
            group = RegressionCalculator.LIST_MEDIUM,
            mode = RegressionCalculator.MODE_STRING,
            sessions = run.listSessions
        ) { it.string },
        row(
            group = RegressionCalculator.LIST_MEDIUM,
            mode = RegressionCalculator.MODE_BYTES,
            sessions = run.listSessions
        ) { it.bytes },
        row(
            group = RegressionCalculator.LIST_MEDIUM,
            mode = RegressionCalculator.MODE_STREAMING,
            sessions = run.listSessions
        ) { it.streaming },
        row(
            group = RegressionCalculator.SYNC_FULL,
            mode = RegressionCalculator.MODE_STRING,
            sessions = run.syncSessions
        ) { it.string },
        row(
            group = RegressionCalculator.SYNC_FULL,
            mode = RegressionCalculator.MODE_BYTES,
            sessions = run.syncSessions
        ) { it.bytes },
        row(
            group = RegressionCalculator.SYNC_FULL,
            mode = RegressionCalculator.MODE_STREAMING,
            sessions = run.syncSessions
        ) { it.streaming },
        row(
            group = RegressionCalculator.WRITING,
            mode = RegressionCalculator.MODE_STRING,
            sessions = run.writingSessions
        ) { it.string },
        row(
            group = RegressionCalculator.WRITING,
            mode = RegressionCalculator.MODE_BYTES,
            sessions = run.writingSessions
        ) { it.bytes },
        row(
            group = RegressionCalculator.WRITING,
            mode = RegressionCalculator.MODE_STREAMING,
            sessions = run.writingSessions
        ) { it.streaming },
    )
}
