@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.GhostJsonReader
import okio.BufferedSource

private var cachedReader: GhostJsonReader? = null

actual fun discoverRegistries(): List<GhostRegistry> = emptyList()

actual fun <T> runSynchronized(lock: Any, block: () -> T): T = block()

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = mutableMapOf<K, V>()

actual fun <T> ghostInternalUseReader(
    bytes: ByteArray, block: (GhostJsonReader) -> T
): T {
    val reader = cachedReader
        ?: GhostJsonReader(bytes)
            .also { cachedReader = it }

    reader.reset(bytes)
    return block(reader)
}

actual fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    val reader = cachedReader
        ?: GhostJsonReader(source)
            .also { cachedReader = it }

    reader.reset(source)
    return block(reader)
}
