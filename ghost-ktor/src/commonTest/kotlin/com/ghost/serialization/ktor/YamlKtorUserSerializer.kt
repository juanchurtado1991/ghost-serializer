@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object YamlKtorUserSerializer : AbstractGhostSerializer<YamlKtorUser>(), GhostYamlSerializer<YamlKtorUser> {
    override val typeName: String = "com.ghost.serialization.ktor.YamlKtorUser"

    override fun serialize(writer: GhostJsonWriter, value: YamlKtorUser) = Unit

    override fun deserialize(reader: GhostJsonReader): YamlKtorUser =
        YamlKtorUser(id = 0, name = "", isActive = false)

    override fun serialize(writer: GhostYamlWriter, value: YamlKtorUser) {
        writer.beginObject()
        writer.name(key = "id")
        writer.value(value.id)
        writer.name(key = "name")
        writer.value(value.name)
        writer.name(key = "isActive")
        writer.value(value.isActive)
        writer.endObject()
    }

    override fun deserialize(reader: GhostYamlFlatReader): YamlKtorUser {
        var id = 0
        var name = ""
        var isActive = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextKey()) {
                "id" -> id = reader.nextInt()
                "name" -> name = reader.nextString()
                "isActive" -> isActive = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return YamlKtorUser(id = id, name = name, isActive = isActive)
    }
}
