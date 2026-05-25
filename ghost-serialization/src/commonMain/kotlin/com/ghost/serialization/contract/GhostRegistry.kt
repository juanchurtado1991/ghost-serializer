package com.ghost.serialization.contract

import kotlin.reflect.KClass

/**
 * Registry interface for discovering and managing compiler-generated and custom serializers.
 *
 * Implementations are typically generated automatically as module-level registries
 * by the Ghost compiler plugin, allowing reflection-free lookup of serializers
 * across all targets in a Kotlin Multiplatform project.
 */
interface GhostRegistry {
    /**
     * Resolves a [GhostSerializer] instance for the given [clazz].
     *
     * @param clazz The [KClass] of the type to retrieve the serializer for.
     * @return The [GhostSerializer] corresponding to [clazz], or `null` if not registered in this module.
     */
    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>?

    /**
     * Deep Prewarm Support: Returns all serializers registered in this module.
     * Ghost uses this for eager loading and zero-latency first-runs.
     *
     * @return A map of [KClass] to its corresponding [GhostSerializer].
     */
    fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = emptyMap()

    /**
     * Eagerly initializes registry entries and prepares internal components to avoid
     * first-use JIT/warm-up latency on critical paths.
     */
    fun prewarm() {
        // Default no-op.
    }

    /**
     * Returns the total number of serializers registered in this registry.
     *
     * @return The count of registered serializers.
     */
    fun registeredCount(): Int = 0
}
