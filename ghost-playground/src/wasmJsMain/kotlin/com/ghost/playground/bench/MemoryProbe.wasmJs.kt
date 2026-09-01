package com.ghost.playground.bench

actual fun currentMemoryUsedBytes(): Long? {
    val used = jsUsedJsHeapSizeOrNegative()
    return if (used < 0.0) null else used.toLong()
}

/** Reads Chrome `performance.memory.usedJSHeapSize`, or `-1` when the API is unavailable. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsUsedJsHeapSizeOrNegative(): Double =
    js("(typeof performance !== 'undefined' && performance.memory ? performance.memory.usedJSHeapSize : -1)")
