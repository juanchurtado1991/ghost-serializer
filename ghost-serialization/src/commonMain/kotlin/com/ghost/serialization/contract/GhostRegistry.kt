package com.ghost.serialization.contract

import kotlin.reflect.KClass

/**
 * Discovers compiler-generated and custom serializers. Implementations are generated
 * per-module by the Ghost compiler plugin for reflection-free lookup across KMP targets.
 */
interface GhostRegistry {
    /** Resolves the [GhostSerializer] for [clazz], or `null` if unregistered in this module. */
    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>?

    /** For eager loading / zero-latency first-runs. */
    fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = emptyMap()

    /** Eagerly initializes registry entries to avoid first-use JIT warm-up latency. */
    fun prewarm() { }

    fun registeredCount(): Int = 0
}
