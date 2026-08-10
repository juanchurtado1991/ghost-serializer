@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.nextLong
import kotlin.reflect.KClass

/**
 * Hand-written stand-in for what
 * `@GhostProtoSerialization` + KSP
 * would generate for `data class ProtoDeviceEvent(val deviceId: Long, val label: String)` —
 * `deviceId` is written as a quoted decimal string (proto3 int64 mapping) and must be readable
 * back as a bare-or-quoted number, exercising exactly what [GhostProtoConverterFactory] depends
 * on (`GhostProtoJsonFlatReader.nextLong` polymorphism via
 * `reader.nextLong()`).
 */
@InternalGhostApi
object ProtoRetrofitTestRegistry : GhostRegistry {
    override fun prewarm() {}
    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
        mapOf(ProtoDeviceEvent::class to ProtoDeviceEventSerializer)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == ProtoDeviceEvent::class) ProtoDeviceEventSerializer as GhostSerializer<T> else null
}
