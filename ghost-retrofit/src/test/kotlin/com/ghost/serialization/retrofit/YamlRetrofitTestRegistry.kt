@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import kotlin.reflect.KClass

object YamlRetrofitTestRegistry : GhostRegistry {
    override fun prewarm() {}
    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
        mapOf(YamlDeviceProfile::class to YamlDeviceProfileSerializer)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == YamlDeviceProfile::class) YamlDeviceProfileSerializer as GhostSerializer<T> else null
}
