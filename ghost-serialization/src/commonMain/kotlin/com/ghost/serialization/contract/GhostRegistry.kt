package com.ghost.serialization.contract

import kotlin.reflect.KClass

/**
 * Discovers compiler-generated and custom serializers. Implementations are generated
 * per-module by the Ghost compiler plugin for reflection-free lookup across KMP targets.
 *
 * [getSerializer] resolves the [GhostSerializer] for a class, or `null` if unregistered in this
 * module. [getAllSerializers] is for eager loading / zero-latency first-runs. [prewarm] eagerly
 * initializes registry entries to avoid first-use JIT warm-up latency. [registeredCount] reports
 * how many serializers this registry holds. All four are abstract — implementations declare
 * explicit behavior for each rather than inheriting a silent default.
 */
interface GhostRegistry {
    fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>>

    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>?

    fun prewarm()

    fun registeredCount(): Int
}
