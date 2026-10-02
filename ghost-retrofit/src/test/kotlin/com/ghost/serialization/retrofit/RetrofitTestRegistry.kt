@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import kotlin.reflect.KClass

@InternalGhostApi
object RetrofitTestRegistry : AbstractGhostRegistry() {
    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
        mapOf(RetrofitUser::class to RetrofitUserSerializer)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == RetrofitUser::class) RetrofitUserSerializer as GhostSerializer<T> else null
}
