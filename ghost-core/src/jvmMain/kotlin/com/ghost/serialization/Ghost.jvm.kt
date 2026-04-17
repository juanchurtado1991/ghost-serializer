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
        "com.ghost.serialization.generated.GhostModuleRegistry_com_ghost_integration"
    )

    patterns.forEach { className ->
        try {
            val clazz = Class.forName(className)
            val registry = try {
                // Try direct Singleton (object) behavior first
                clazz.getDeclaredField("INSTANCE").get(null) as? GhostRegistry
            } catch (_: Exception) {
                // Fallback to Companion object behavior
                try {
                    val companion = clazz.getDeclaredField("Companion").get(null)
                    companion.javaClass.getDeclaredField("INSTANCE").get(companion) as? GhostRegistry
                } catch (_: Exception) {
                    null
                }
            }
            if (registry != null) registries.add(registry)
        } catch (_: Exception) { }
    }
    return registries
}
