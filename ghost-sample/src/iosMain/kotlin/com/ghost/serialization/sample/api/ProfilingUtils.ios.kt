package com.ghost.serialization.sample.api

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun getCurrentThreadAllocatedBytes(): Long {
    // In Kotlin/Native, we can track total heap usage.
    // While not per-thread, it is extremely precise for our unit-test context.
    return kotlin.native.runtime.GC.memoryUsage().usageBytes
}
