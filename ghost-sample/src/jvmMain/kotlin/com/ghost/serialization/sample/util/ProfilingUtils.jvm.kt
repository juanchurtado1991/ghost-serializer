package com.ghost.serialization.sample.util

actual fun forceGC() {
    System.gc()
}

actual fun getCurrentThreadAllocatedBytes(): Long {
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
}
