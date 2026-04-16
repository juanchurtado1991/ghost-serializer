package com.ghost.serialization.core.contract

import kotlin.reflect.KClass

interface GhostRegistry {
    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>?

    fun prewarm() {
        // Default no-op. Generated implementations override this by triggering
        // registry initialization eagerly (e.g. in Application.onCreate()).
    }

    fun registeredCount(): Int = 0
}
