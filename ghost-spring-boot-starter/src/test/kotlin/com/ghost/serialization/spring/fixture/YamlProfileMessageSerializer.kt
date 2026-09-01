@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.spring.fixture

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object YamlProfileMessageSerializer :
    GhostSerializer<YamlProfileMessage>,
    GhostYamlSerializer<YamlProfileMessage> {
    override val typeName: String = "com.ghost.serialization.spring.fixture.YamlProfileMessage"

    override fun serialize(writer: GhostJsonWriter, value: YamlProfileMessage) = Unit
    override fun deserialize(reader: GhostJsonReader): YamlProfileMessage =
        YamlProfileMessage(0, "")

    override fun serialize(writer: GhostYamlWriter, value: YamlProfileMessage) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id)
        writer.name("name")
        writer.value(value.name)
        writer.endObject()
    }

    override fun deserialize(reader: GhostYamlFlatReader): YamlProfileMessage {
        var id = 0
        var name = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextKey()) {
                "id" -> id = reader.nextInt()
                "name" -> name = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return YamlProfileMessage(id, name)
    }
}
