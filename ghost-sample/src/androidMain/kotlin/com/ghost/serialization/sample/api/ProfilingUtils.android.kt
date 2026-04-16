package com.ghost.serialization.sample.api

actual fun getCurrentThreadAllocatedBytes(): Long {
    // Android doesn't expose thread-specific allocation easily in the SDK.
    // We use a Runtime heap delta as a fallback estimate.
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
}
