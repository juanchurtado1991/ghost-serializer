@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.GhostJsonReader
import okio.BufferedSource
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Android-specific implementation for Ghost Serialization discovery.
 * Hybrid approach for maximum startup performance.
 */

private const val REGISTRY_CLASS =
    "com.ghost.serialization.generated.GhostModuleRegistry_ghost_serialization"
private const val INSTANCE_FILED = "INSTANCE"

private val readerPool = ThreadLocal<GhostJsonReader>()

actual fun discoverRegistries(): List<GhostRegistry> {
    val registries = linkedSetOf<GhostRegistry>()

    // 1. Direct bypass (Zero latency for core)
    runCatching {
        val instance = Class
            .forName(REGISTRY_CLASS)
            .getDeclaredField(INSTANCE_FILED)
            .get(null) as GhostRegistry

        registries.add(instance)
    }

    runCatching {
        val loader = ServiceLoader
            .load(GhostRegistry::class.java)

        registries.addAll(loader)
    }

    return registries.toList()
}

actual fun <T> runSynchronized(lock: Any, block: () -> T): T = synchronized(lock, block)

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = ConcurrentHashMap()

actual fun <T> ghostInternalUseReader(
    bytes: ByteArray, block: (GhostJsonReader) -> T
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
