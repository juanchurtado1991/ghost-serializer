package com.ghost.serialization.sample.util

import android.annotation.SuppressLint

/**
 * Utility to force Garbage Collection if the platform supports it.
 */
actual fun forceGC() {
    System.gc()
    Runtime.getRuntime().gc()
    try { Thread.sleep(100) } catch (_: Exception) {}
}

/**
 * Gets the allocated bytes on the current thread using VMDebug for byte-precision.
 */
@SuppressLint("SoonBlockedPrivateApi")
actual fun getCurrentThreadAllocatedBytes(): Long {
    return try {
        val vmDebugClass = Class.forName("dalvik.system.VMDebug")
        val method = vmDebugClass.getMethod("threadAllocSize")
        method.invoke(null) as Long
    } catch (_: Exception) {
        val runtime = Runtime.getRuntime()
        runtime.totalMemory() - runtime.freeMemory()
    }
}
