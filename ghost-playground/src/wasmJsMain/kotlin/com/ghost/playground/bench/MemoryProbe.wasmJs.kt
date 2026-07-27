package com.ghost.playground.bench

actual fun currentMemoryUsedBytes(): Long? {
    val used = jsUsedJsHeapSizeOrNegative()
    return if (used < 0.0) null else used.toLong()
}

/** Chrome-only `performance.memory.usedJSHeapSize`; returns -1 where unsupported (Firefox/Safari). */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun jsUsedJsHeapSizeOrNegative(): Double =
    js("(typeof performance !== 'undefined' && performance.memory ? performance.memory.usedJSHeapSize : -1)")
