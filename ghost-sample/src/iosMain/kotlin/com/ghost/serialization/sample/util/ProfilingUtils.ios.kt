@file:OptIn(NativeRuntimeApi::class)

package com.ghost.serialization.sample.util

import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

actual fun forceGC() {
    GC.collect()
}

actual fun getCurrentThreadAllocatedBytes(): Long {
    // Memory tracking on Native is more complex due to ARC.
    return 0L
}
