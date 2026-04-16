package com.ghost.serialization

import com.ghost.serialization.core.contract.GhostRegistry
import java.util.ServiceLoader

/**
 * JVM Implementation of Ghost Registry Discovery.
 * Uses ServiceLoader to aggregate all module registries from the classpath.
 */
actual fun discoverRegistries(): List<GhostRegistry> {
    val discovered = ServiceLoader.load(GhostRegistry::class.java).toList()
    if (discovered.isNotEmpty()) return discovered

    // Fallback for classpath issues during development or complex KMP layouts
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
            // Ignore
        }
    }
    return registries
}
