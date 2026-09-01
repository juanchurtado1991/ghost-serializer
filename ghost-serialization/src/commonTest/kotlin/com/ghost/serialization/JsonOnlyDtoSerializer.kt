package com.ghost.serialization

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter

internal object JsonOnlyDtoSerializer : GhostSerializer<JsonOnlyDto> {
    override val typeName: String = "JsonOnlyDto"
    override fun serialize(writer: GhostJsonWriter, value: JsonOnlyDto) = Unit

    override fun deserialize(reader: GhostJsonReader): JsonOnlyDto =
        JsonOnlyDto(0)

    override fun deserialize(reader: GhostJsonStringReader): JsonOnlyDto =
        JsonOnlyDto(0)
}
