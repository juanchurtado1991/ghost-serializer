package com.ghost.serialization

import com.ghost.serialization.core.contract.GhostRegistry
import java.util.ServiceLoader

/**
 * Android-specific implementation for Ghost Serialization discovery.
 * Hybrid approach for maximum startup performance.
 */
actual fun discoverRegistries(): List<GhostRegistry> {
    val registries = mutableListOf<GhostRegistry>()
    
    // 1. Direct bypass (Zero latency for core)
    try {
        val registryClass = Class.forName("com.ghost.serialization.benchmark.GhostModuleRegistry_ghost_serialization")
        val instance = registryClass.getDeclaredField("INSTANCE").get(null) as GhostRegistry
        registries.add(instance)
    } catch (e: Exception) {
    }

    // 2. ServiceLoader fallback
    try {
        val loader = ServiceLoader.load(GhostRegistry::class.java)
        for (registry in loader) {
            if (!registries.contains(registry)) {
                registries.add(registry)
            }
        }
    } catch (e: Exception) {
    }
    
    return registries
}

actual fun <T> runSynchronized(lock: Any, block: () -> T): T {
    return synchronized(lock, block)
}

actual fun <T> ghostIternalUseReader(bytes: ByteArray, block: (com.ghost.serialization.core.parser.GhostJsonReader) -> T): T {
    val reader = com.ghost.serialization.core.parser.GhostJsonReader(bytes)
    return block(reader)
}

actual fun <T> ghostInternalUseSource(source: okio.BufferedSource, block: (com.ghost.serialization.core.parser.GhostJsonReader) -> T): T {
    val reader = com.ghost.serialization.core.parser.GhostJsonReader(source.readByteArray())
    return block(reader)
}
