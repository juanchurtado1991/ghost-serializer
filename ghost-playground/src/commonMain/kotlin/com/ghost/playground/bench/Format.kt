package com.ghost.playground.bench

import kotlin.math.abs
import kotlin.math.round

private const val THOUSAND = 1_000.0
private const val MILLION = 1_000_000.0

private const val BYTES_PER_KB = 1_024.0
private const val BYTES_PER_MB = 1_048_576.0
private const val BYTES_PER_GB = 1_073_741_824.0

private const val SECONDS_PER_MINUTE = 60

fun formatBytes(bytes: Long): String {
    val v = bytes.toDouble()
    return when {
        v >= BYTES_PER_GB -> "${roundTo(v / BYTES_PER_GB, 2)} GB"
        v >= BYTES_PER_MB -> "${roundTo(v / BYTES_PER_MB, 1)} MB"
        v >= BYTES_PER_KB -> "${roundTo(v / BYTES_PER_KB, 1)} KB"
        else -> "$bytes B"
    }
}

fun formatCompactNumber(value: Double): String = when {
    value >= MILLION -> "${roundTo(value / MILLION, 1)}M"
    value >= THOUSAND -> "${roundTo(value / THOUSAND, 1)}K"
    else -> roundTo(value = value, decimals = 0)
}

fun formatSeconds(totalSeconds: Double): String {
    val minutes = (totalSeconds / SECONDS_PER_MINUTE).toLong()
    val seconds = (totalSeconds - minutes * SECONDS_PER_MINUTE)
    return if (minutes > 0) {
        "${minutes}m ${roundTo(seconds, 0)}s"
    } else {
        "${roundTo(seconds, 1)}s"
    }
}

/** Cross-platform decimal formatting; `String.format` and `%.1f` are unavailable in commonMain. */
fun roundTo(value: Double, decimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    val factor = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        else -> 1000.0
    }
    val rounded = round(value * factor) / factor
    val intPart = rounded.toLong()
    if (decimals == 0) return intPart.toString()
    val fracPart = abs(round((rounded - intPart) * factor)).toLong()
    return "$intPart.${fracPart.toString().padStart(decimals, '0')}"
}
