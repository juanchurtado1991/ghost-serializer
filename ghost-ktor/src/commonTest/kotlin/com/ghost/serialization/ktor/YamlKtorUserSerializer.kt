@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import io.ktor.http.ContentType

private val YAML_MEDIA_TYPE = ContentType(CONTENT_TYPE_APPLICATION, CONTENT_TYPE_YAML)

object YamlKtorUserSerializer : GhostSerializer<YamlKtorUser>, GhostYamlSerializer<YamlKtorUser> {
    override val typeName: String = "com.ghost.serialization.ktor.YamlKtorUser"

    override fun serialize(writer: GhostJsonWriter, value: YamlKtorUser) = Unit

    override fun deserialize(reader: GhostJsonReader): YamlKtorUser =
        YamlKtorUser(0, "", false)

    override fun serialize(writer: GhostYamlWriter, value: YamlKtorUser) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id)
        writer.name("name")
        writer.value(value.name)
        writer.name("isActive")
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
        return YamlKtorUser(id, name, isActive)
    }
}
