package com.ghost.serialization.contract

import kotlin.reflect.KClass

/**
 * Skeletal [GhostRegistry] — extend this instead of implementing [GhostRegistry] directly for the
 * defaults most implementations don't need to customize: [getAllSerializers] returns an empty
 * map, [prewarm] is a no-op, [registeredCount] derives from [getAllSerializers].
 */
abstract class AbstractGhostRegistry : GhostRegistry {

    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = emptyMap()

    override fun prewarm() {}

    override fun registeredCount(): Int = getAllSerializers().size
}
