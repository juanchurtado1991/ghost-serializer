package com.ghost.serialization.sample.api

import java.lang.management.ManagementFactory

actual fun getCurrentThreadAllocatedBytes(): Long {
    val bean = ManagementFactory.getThreadMXBean()
    return if (bean is com.sun.management.ThreadMXBean && bean.isThreadAllocatedMemorySupported) {
        if (!bean.isThreadAllocatedMemoryEnabled) {
            try {
                bean.isThreadAllocatedMemoryEnabled = true
            } catch (e: Exception) {
                return 0L
            }
        }
        bean.getThreadAllocatedBytes(Thread.currentThread().id)
    } else {
        0L
    }
}
