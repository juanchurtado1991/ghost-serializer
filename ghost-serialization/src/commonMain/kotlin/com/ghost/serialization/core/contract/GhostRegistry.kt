package com.ghost.serialization.core.contract

import kotlin.reflect.KClass

interface GhostRegistry {
    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>?

    /**
     * Deep Prewarm Support: Returns all serializers registered in this module.
     * Ghost uses this for eager loading and zero-latency first-runs.
     */
    fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = emptyMap()

    fun prewarm() {
        // Default no-op.
    }

    fun registeredCount(): Int = 0
}
