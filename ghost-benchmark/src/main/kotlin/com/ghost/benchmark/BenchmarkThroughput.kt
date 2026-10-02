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

    private const val MICROS_PER_SECOND = 1_000_000.0
    private const val NANOS_PER_MICRO = 1_000.0
    private const val NANOS_PER_SECOND = 1_000_000_000.0

    private const val FORMAT_GB_PER_SEC_PADDED = "%7.3f        "
    private const val FORMAT_GB_PER_SEC_WITH_STDEV = "%7.3f ±%-5.3f"
    private const val FORMAT_MICROS_PADDED = "%7.1f        "
    private const val FORMAT_MICROS_WITH_STDEV = "%7.1f ±%-5.1f"

    /** Formats mean GB/s with optional ± stdev when [stdev] is positive. */
    fun formatGbPerSecWithStdev(mean: Double, stdev: Double): String {
        return if (stdev > 0.0) {
            FORMAT_GB_PER_SEC_WITH_STDEV.format(mean, stdev)
        } else {
            FORMAT_GB_PER_SEC_PADDED.format(mean)
        }
    }

    /** Formats mean µs/op with optional ± stdev when [stdev] is positive. */
    fun formatMicrosWithStdev(mean: Double, stdev: Double): String {
        return if (stdev > 0.0) {
            FORMAT_MICROS_WITH_STDEV.format(mean, stdev)
        } else {
            FORMAT_MICROS_PADDED.format(mean)
        }
    }

    fun microsToGbPerSec(microsPerOp: Double, payloadBytes: Long): Double {
        if (microsPerOp <= 0.0 || payloadBytes <= 0L) return 0.0
        val seconds = microsPerOp / MICROS_PER_SECOND
        return payloadBytes / seconds / BYTES_PER_GB
    }

    /**
     * Propagates a latency stdev (nanoseconds) into a GB/s stdev via linearization:
     * `σ_gb ≈ gb × (σ_ns / mean_ns)`.
     */
    fun nanosStdevToGbPerSec(meanNanos: Long, stdevNanos: Long, payloadBytes: Long): Double {
        if (meanNanos <= 0L) return 0.0
        val meanGb = nanosToGbPerSec(nanosPerOp = meanNanos, payloadBytes = payloadBytes)
        return meanGb * (stdevNanos.toDouble() / meanNanos.toDouble())
    }

    fun nanosStdevToMicros(stdevNanos: Long): Double = stdevNanos / NANOS_PER_MICRO

    /**
     * Converts a per-op latency in nanoseconds into GB/s.
     * Zero / negative latency returns 0 (avoids divide-by-zero on empty metrics).
     */
    fun nanosToGbPerSec(nanosPerOp: Long, payloadBytes: Long): Double {
        if (nanosPerOp <= 0L || payloadBytes <= 0L) return 0.0
        val seconds = nanosPerOp / NANOS_PER_SECOND
        return payloadBytes / seconds / BYTES_PER_GB
    }

    fun nanosToMicros(nanosPerOp: Long): Double = nanosPerOp / NANOS_PER_MICRO

    /** Converts absolute throughput in ops/s into GB/s for a known payload size. */
    fun opsPerSecToGbPerSec(opsPerSec: Double, payloadBytes: Long): Double {
        return opsPerSec * payloadBytes / BYTES_PER_GB
    }

    /** Converts absolute throughput in ops/s into per-op latency in µs. */
    fun opsPerSecToMicros(opsPerSec: Double): Double {
        if (opsPerSec <= 0.0) return 0.0
        return MICROS_PER_SECOND / opsPerSec
    }
}
