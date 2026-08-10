package com.ghost.serialization

import com.ghost.serialization.contract.GhostSerializer

internal object JsonOnlyDtoSerializer : GhostSerializer<JsonOnlyDto> {
    override val typeName: String = "JsonOnlyDto"
    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonWriter,
        value: JsonOnlyDto
    ) = Unit

    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter,
        value: JsonOnlyDto
    ) = Unit

    override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): JsonOnlyDto =
        JsonOnlyDto(0)

    override fun deserialize(reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader): JsonOnlyDto =
        JsonOnlyDto(0)

    override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): JsonOnlyDto =
        JsonOnlyDto(0)
}
