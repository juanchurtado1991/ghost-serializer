package com.ghost.serialization

import com.ghost.serialization.core.contract.GhostRegistry

/**
 * Android/JVM Implementation of Ghost Registry Discovery.
 * Superiority: Zero-config automatic discovery.
 */
actual fun discoverRegistries(): List<GhostRegistry> {
    // 1. Try ServiceLoader (High Performance Industrial Standard)
    try {
        val discovered = java.util.ServiceLoader.load(GhostRegistry::class.java).toList()
        if (discovered.isNotEmpty()) return discovered
    } catch (e: Exception) {
        // Fallback to reflection if ServiceLoader fails on older Android versions
    }

    // 2. Reflective Fallback for complex KMP environments or R8 issues
    val registries = mutableListOf<GhostRegistry>()
    val patterns = listOf(
        "com.ghost.serialization.generated.GhostModuleRegistry_Default",
        "com.ghost.serialization.generated.GhostModuleRegistry_com_ghost_serialization_sample_domain",
        "com.ghost.serialization.generated.GhostModuleRegistry_com_ghost_integration_model"
    )

    patterns.forEach { className ->
        try {
            val registry = Class.forName(className)
                .getDeclaredField("INSTANCE")
                .get(null) as? GhostRegistry
            if (registry != null) registries.add(registry)
        } catch (e: Exception) {
            // Ignore missing registries
        }
    }
    return registries
}
