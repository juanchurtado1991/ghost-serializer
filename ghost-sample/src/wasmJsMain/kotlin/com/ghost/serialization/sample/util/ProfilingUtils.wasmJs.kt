package com.ghost.serialization.sample.util

import kotlin.js.JsFun

@JsFun("() => { if (typeof globalThis !== 'undefined' && typeof globalThis.gc === 'function') { globalThis.gc(); } else if (typeof window !== 'undefined' && typeof window.gc === 'function') { window.gc(); } else if (typeof gc === 'function') { gc(); } }")
private external fun triggerJsGC()

actual fun forceGC() {
    runCatching { triggerJsGC() }
}

@JsFun("() => { if (typeof process !== 'undefined' && typeof process.memoryUsage === 'function') { return process.memoryUsage().heapUsed; } if (typeof window !== 'undefined' && typeof window.performance !== 'undefined' && typeof window.performance.memory !== 'undefined') { return window.performance.memory.usedJSHeapSize; } return 0; }")
private external fun getJsHeapUsed(): Double

actual fun getCurrentThreadAllocatedBytes(): Long {
    return getJsHeapUsed().toLong()
}
