package com.ghost.serialization.sample.api

actual fun getCurrentThreadAllocatedBytes(): Long {
    return try {
        // VMDebug is the internal truth of Dalvik/ART.
        // threadAllocSize() provides byte-precision that android.os.Debug might aggregate in 32KB chunks.
        val vmDebugClass = Class.forName("dalvik.system.VMDebug")
        val method = vmDebugClass.getMethod("threadAllocSize")
        method.invoke(null) as Long
    } catch (e: Exception) {
        // Last-ditch effort if VMDebug is missing
        val runtime = Runtime.getRuntime()
        runtime.totalMemory() - runtime.freeMemory()
    }
}
