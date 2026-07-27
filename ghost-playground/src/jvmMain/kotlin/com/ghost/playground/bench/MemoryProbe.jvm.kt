package com.ghost.playground.bench

actual fun currentMemoryUsedBytes(): Long? {
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
}
