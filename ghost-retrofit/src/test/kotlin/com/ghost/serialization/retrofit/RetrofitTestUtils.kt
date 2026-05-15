@file:OptIn(InternalGhostApi::class)
package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.nextBoolean
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.skipValue
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter
import kotlin.reflect.KClass

@InternalGhostApi
object RetrofitUserSerializer : GhostSerializer<RetrofitUser> {
    override val typeName: String = "com.ghost.serialization.retrofit.RetrofitUser"

    override fun serialize(writer: GhostJsonWriter, value: RetrofitUser) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id.toLong())
        writer.name("name")
        writer.value(value.name)
        writer.name("isActive")
        writer.value(value.isActive)
        writer.endObject()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: RetrofitUser) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id.toLong())
        writer.name("name")
        writer.value(value.name)
        writer.name("isActive")
        writer.value(value.isActive)
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): RetrofitUser {
        var id = 0
        var name = ""
        var isActive = false
        reader.beginObject()
        while (true) {
            val key = reader.nextKey() ?: break
            reader.consumeKeySeparator()
            when (key) {
                "id" -> id = reader.nextInt()
                "name" -> name = reader.nextString()
                "isActive" -> isActive = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return RetrofitUser(id, name, isActive)
    }
}

@InternalGhostApi
object RetrofitTestRegistry : GhostRegistry {
    override fun prewarm() {}
    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
        mapOf(RetrofitUser::class to RetrofitUserSerializer)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == RetrofitUser::class) RetrofitUserSerializer as GhostSerializer<T> else null
}

data class RetrofitUser(val id: Int, val name: String, val isActive: Boolean)
