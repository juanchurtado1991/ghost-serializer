package com.ghost.serialization

import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter

internal object JsonOnlyDtoSerializer : AbstractGhostSerializer<JsonOnlyDto>() {
    override val typeName: String = "JsonOnlyDto"
    override fun serialize(writer: GhostJsonWriter, value: JsonOnlyDto) = Unit

    override fun deserialize(reader: GhostJsonReader): JsonOnlyDto =
        JsonOnlyDto(id = 0)

    override fun deserialize(reader: GhostJsonStringReader): JsonOnlyDto =
        JsonOnlyDto(id = 0)
}
