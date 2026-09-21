@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import kotlin.reflect.KClass

/** Test registry exposing [ProtoDeviceEventSerializer]. */
@InternalGhostApi
object ProtoRetrofitTestRegistry : GhostRegistry {
    override fun prewarm() {}
    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
        mapOf(ProtoDeviceEvent::class to ProtoDeviceEventSerializer)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == ProtoDeviceEvent::class) ProtoDeviceEventSerializer as GhostSerializer<T> else null
}
