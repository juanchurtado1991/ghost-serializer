package com.ghost.serialization

import com.ghost.serialization.core.GhostRegistry

/**
 * Android/JVM Implementation of Ghost Registry Discovery.
 * Superiority: Zero-config automatic discovery.
 */
actual fun discoverRegistries(): List<GhostRegistry> {
    // On Android, we prefer the reflective lookup to the generated registry
    // to avoid ServiceLoader issues in some obfuscation scenarios.
    return try {
        val registry = Class.forName("com.ghost.serialization.generated.GhostModuleRegistry")
            .getDeclaredField("INSTANCE")
            .get(null) as? GhostRegistry
        if (registry != null) listOf(registry) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
