package com.ghost.serialization

// Kotlin/Wasm browser runtime is single-threaded — plain maps and an unlocked block are
// correct and cheaper than the native thread-safe equivalents.
actual fun <K, V> createAtomicMap(): MutableMap<K, V> = mutableMapOf()

actual fun <T> runSynchronized(
    lock: Any,
    block: () -> T
): T = block()
