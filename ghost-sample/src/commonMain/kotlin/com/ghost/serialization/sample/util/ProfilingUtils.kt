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
