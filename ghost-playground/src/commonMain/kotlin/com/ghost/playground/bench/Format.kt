package com.ghost.playground.bench

import kotlin.math.abs
import kotlin.math.round

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

fun formatCompactNumber(value: Double): String = when {
    value >= 1_000_000 -> "${roundTo(value / 1_000_000, 1)}M"
    value >= 1_000 -> "${roundTo(value / 1_000, 1)}K"
    else -> roundTo(value, 0)
}

fun formatBytes(bytes: Long): String {
    val v = bytes.toDouble()
    return when {
        v >= 1_073_741_824.0 -> "${roundTo(v / 1_073_741_824.0, 2)} GB"
        v >= 1_048_576.0 -> "${roundTo(v / 1_048_576.0, 1)} MB"
        v >= 1_024.0 -> "${roundTo(v / 1_024.0, 1)} KB"
        else -> "$bytes B"
    }
}

fun formatSeconds(totalSeconds: Double): String {
    val minutes = (totalSeconds / 60).toLong()
    val seconds = (totalSeconds - minutes * 60)
    return if (minutes > 0) {
        "${minutes}m ${roundTo(seconds, 0)}s"
    } else {
        "${roundTo(seconds, 1)}s"
    }
}
