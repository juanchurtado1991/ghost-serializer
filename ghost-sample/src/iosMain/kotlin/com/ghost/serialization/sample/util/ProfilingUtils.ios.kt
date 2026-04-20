package com.ghost.serialization.sample.util

import kotlin.native.internal.GC

actual fun forceGC() {
    GC.collect()
}

actual fun getCurrentThreadAllocatedBytes(): Long {
    // Memory tracking on Native is more complex. 
    // For now, we return 0 as a placeholder or use platform-specific hooks.
    return 0L
}
