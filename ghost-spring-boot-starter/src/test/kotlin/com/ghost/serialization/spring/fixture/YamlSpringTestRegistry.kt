@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.spring.fixture

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import kotlin.reflect.KClass

object YamlSpringTestRegistry : GhostRegistry {
    override fun prewarm() {}
    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
        mapOf(YamlProfileMessage::class to YamlProfileMessageSerializer)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == YamlProfileMessage::class) YamlProfileMessageSerializer as GhostSerializer<T> else null
}
