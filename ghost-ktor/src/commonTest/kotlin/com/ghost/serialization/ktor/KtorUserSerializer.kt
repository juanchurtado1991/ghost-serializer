@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.writer.bytes.GhostJsonWriter

// --- Mock Models ---
object KtorUserSerializer : GhostSerializer<KtorUser> {
    override val typeName: String = "com.ghost.serialization.ktor.KtorUser"

    override fun serialize(writer: GhostJsonWriter, value: KtorUser) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id.toLong())
        writer.name("name")
        writer.value(value.name)
        writer.name("isActive")
        writer.value(value.isActive)
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): KtorUser {
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
        return KtorUser(id, name, isActive)
    }
}
