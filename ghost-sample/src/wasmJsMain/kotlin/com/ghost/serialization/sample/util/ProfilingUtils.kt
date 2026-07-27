package com.ghost.serialization.sample.util

actual fun forceGC() {
    // No explicit GC control on Kotlin/Wasm.
}

actual fun getCurrentThreadAllocatedBytes(): Long = 0L
