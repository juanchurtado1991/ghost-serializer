package com.ghost.serialization

import com.ghost.serialization.core.GhostRegistry
import java.util.ServiceLoader

/**
 * JVM Implementation of Ghost Registry Discovery.
 * Uses ServiceLoader to aggregate all module registries from the classpath.
 */
actual fun discoverRegistries(): List<GhostRegistry> {
    return ServiceLoader.load(GhostRegistry::class.java).toList().ifEmpty {
        // Fallback for classpath issues during development
        try {
            val fallback = Class.forName("com.ghost.serialization.generated.GhostModuleRegistry")
                .getDeclaredField("INSTANCE")
                .get(null) as? GhostRegistry
            if (fallback != null) listOf(fallback) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
