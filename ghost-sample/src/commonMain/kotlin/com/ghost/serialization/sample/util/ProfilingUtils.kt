package com.ghost.serialization.sample.util

/**
 * Utility to force Garbage Collection if the platform supports it.
 * Essential for fair memory benchmarking.
 */
expect fun forceGC()

/**
 * Gets the allocated bytes on the current thread (if supported).
 */
expect fun getCurrentThreadAllocatedBytes(): Long

/**
 * Human readable memory formatting.
 */
fun formatMem(bytes: Long): String {
    val b = if (bytes < 0) 0L else bytes
    return when {
        b >= 1024 * 1024 -> "${(b / (1024 * 1024.0) * 100).toInt() / 100.0} MB"
        b >= 1024 -> "${(b / 1024.0 * 100).toInt() / 100.0} KB"
        else -> "$b B"
    }
}
