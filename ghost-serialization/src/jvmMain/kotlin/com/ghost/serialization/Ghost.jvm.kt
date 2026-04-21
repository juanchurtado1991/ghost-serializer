package com.ghost.serialization

import com.ghost.serialization.core.contract.GhostRegistry
import java.util.ServiceLoader

/**
 * JVM-specific implementation for Ghost Serialization discovery.
 * Uses a hybrid approach: Direct load for core + ServiceLoader for modules.
 */
actual fun discoverRegistries(): List<GhostRegistry> {
    val registries = mutableListOf<GhostRegistry>()
    
    // 1. Direct bypass for maximum performance (Zero latency for core)
    try {
        val registryClass = Class.forName("com.ghost.serialization.benchmark.GhostModuleRegistry_ghost_serialization")
        val instance = registryClass.getDeclaredField("INSTANCE").get(null) as GhostRegistry
        registries.add(instance)
    } catch (e: Exception) {
        // Core registry not found, fallback to ServiceLoader
    }

    // 2. ServiceLoader for modularity (other modules)
    try {
        val loader = ServiceLoader.load(GhostRegistry::class.java)
        for (registry in loader) {
            if (!registries.contains(registry)) {
                registries.add(registry)
            }
        }
    } catch (e: Exception) {
        // ServiceLoader failed
    }
    
    return registries
}

actual fun <T> runSynchronized(lock: Any, block: () -> T): T {
    return synchronized(lock, block)
}

actual fun <T> ghostInternalUseReader(bytes: ByteArray, block: (com.ghost.serialization.core.parser.GhostJsonReader) -> T): T {
    val reader = com.ghost.serialization.core.parser.GhostJsonReader(bytes)
    return block(reader)
}

actual fun <T> ghostInternalUseSource(source: okio.BufferedSource, block: (com.ghost.serialization.core.parser.GhostJsonReader) -> T): T {
    val reader = com.ghost.serialization.core.parser.GhostJsonReader(source.readByteArray())
    return block(reader)
}
