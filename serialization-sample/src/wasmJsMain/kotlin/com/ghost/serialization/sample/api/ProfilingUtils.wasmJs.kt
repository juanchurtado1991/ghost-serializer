package com.ghostserializer.sample.api

/**
 * Wasm Implementation: Standardized metrics.
 * Memory allocation tracking per thread is not available in the browser (Wasm).
 * Returns 0 to allow benchmark consistency.
 */
actual fun getCurrentThreadAllocatedBytes(): Long = 0L
