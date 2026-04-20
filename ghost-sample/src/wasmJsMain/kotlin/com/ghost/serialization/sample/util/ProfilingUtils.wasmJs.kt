package com.ghost.serialization.sample.util

actual fun forceGC() {
    // No standard way to force GC in WASM/JS
}

actual fun getCurrentThreadAllocatedBytes(): Long {
    return 0L
}
