package com.ghost.serialization.sample.api

import java.lang.management.ManagementFactory

actual fun getCurrentThreadAllocatedBytes(): Long {
    val bean = ManagementFactory.getThreadMXBean()
    return if (bean is com.sun.management.ThreadMXBean && bean.isThreadAllocatedMemorySupported) {
        if (!bean.isThreadAllocatedMemoryEnabled) {
            bean.isThreadAllocatedMemoryEnabled = true
        }
        bean.getThreadAllocatedBytes(Thread.currentThread().id)
    } else {
        0L
    }
}
