@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.GhostJsonReader
import okio.BufferedSource
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

private val readerPool = ThreadLocal<GhostJsonReader>()

actual fun <T> runSynchronized(lock: Any, block: () -> T): T = synchronized(lock, block)

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = ConcurrentHashMap()

actual fun discoverRegistries(): List<GhostRegistry> {
    val registries = linkedSetOf<GhostRegistry>()

    runCatching {
        val loader = ServiceLoader
            .load(GhostRegistry::class.java)

        registries.addAll(loader)
    }

    return registries.toList()
}

actual fun <T> ghostInternalUseReader(
    bytes: ByteArray,
    block: (GhostJsonReader) -> T
): T {
    val reader = readerPool.get()
        ?: GhostJsonReader(bytes)
            .also { readerPool.set(it) }

    reader.reset(bytes)
    return block(reader)
}

actual fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    val reader = readerPool.get()
        ?: GhostJsonReader(source)
            .also { readerPool.set(it) }

    reader.reset(source)
    return block(reader)
}
