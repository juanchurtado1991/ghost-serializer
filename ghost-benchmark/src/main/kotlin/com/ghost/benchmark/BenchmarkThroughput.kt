package com.ghost.benchmark

/**
 * Shared throughput formatting for every benchmark table.
 *
 * Tables report **both**:
 * - **µs/op** — absolute per-operation latency (easy to reason about)
 * - **decimal GB/s** (SI: 1 GB = 10⁹ bytes) — `payloadBytes / seconds / 1e9`
 *
 * Relative rankings (Ghost÷KSER) are unchanged by the unit conversion.
 */
internal object BenchmarkThroughput {
    /** SI gigabyte in bytes (10⁹), matching network bandwidth conventions. */
    const val BYTES_PER_GB = 1_000_000_000.0

    /** Bytes in the Twitter macro fixture — used for ops/s → GB/s conversion. */
    const val TWITTER_PAYLOAD_BYTES = 631_514L

    /** Converts absolute throughput in ops/s into per-op latency in µs. */
    fun opsPerSecToMicros(opsPerSec: Double): Double {
        if (opsPerSec <= 0.0) return 0.0
        return 1_000_000.0 / opsPerSec
    }

    /** Converts absolute throughput in ops/s into GB/s for a known payload size. */
    fun opsPerSecToGbPerSec(opsPerSec: Double, payloadBytes: Long): Double {
        return opsPerSec * payloadBytes / BYTES_PER_GB
    }

    /**
     * Converts a per-op latency in nanoseconds into GB/s.
     * Zero / negative latency returns 0 (avoids divide-by-zero on empty metrics).
     */
    fun nanosToGbPerSec(nanosPerOp: Long, payloadBytes: Long): Double {
        if (nanosPerOp <= 0L || payloadBytes <= 0L) return 0.0
        val seconds = nanosPerOp / 1_000_000_000.0
        return payloadBytes / seconds / BYTES_PER_GB
    }

    /** Nanoseconds → µs/op. */
    fun nanosToMicros(nanosPerOp: Long): Double = nanosPerOp / 1_000.0

    /** Nanoseconds stdev → µs stdev. */
    fun nanosStdevToMicros(stdevNanos: Long): Double = stdevNanos / 1_000.0

    /** Converts a µs/op latency into GB/s. */
    fun microsToGbPerSec(microsPerOp: Double, payloadBytes: Long): Double {
        if (microsPerOp <= 0.0 || payloadBytes <= 0L) return 0.0
        val seconds = microsPerOp / 1_000_000.0
        return payloadBytes / seconds / BYTES_PER_GB
    }

    /**
     * Propagates a latency stdev (nanoseconds) into a GB/s stdev via linearization:
     * `σ_gb ≈ gb × (σ_ns / mean_ns)`.
     */
    fun nanosStdevToGbPerSec(meanNanos: Long, stdevNanos: Long, payloadBytes: Long): Double {
        if (meanNanos <= 0L) return 0.0
        val meanGb = nanosToGbPerSec(meanNanos, payloadBytes)
        return meanGb * (stdevNanos.toDouble() / meanNanos.toDouble())
    }

    /** Formats a GB/s throughput value with three decimal places. */
    fun formatGbPerSec(value: Double): String = "%.3f".format(value)

    /** Formats a µs/op latency value with one decimal place. */
    fun formatMicros(value: Double): String = "%.1f".format(value)

    /** Formats mean µs/op with optional ± stdev when [stdev] is positive. */
    fun formatMicrosWithStdev(mean: Double, stdev: Double): String {
        return if (stdev > 0.0) {
            "%7.1f ±%-5.1f".format(mean, stdev)
        } else {
            "%7.1f        ".format(mean)
        }
    }

    /** Formats mean GB/s with optional ± stdev when [stdev] is positive. */
    fun formatGbPerSecWithStdev(mean: Double, stdev: Double): String {
        return if (stdev > 0.0) {
            "%7.3f ±%-5.3f".format(mean, stdev)
        } else {
            "%7.3f        ".format(mean)
        }
    }
}
