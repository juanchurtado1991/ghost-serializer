package com.ghost.serialization

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

internal object YamlCapableDtoSerializer :
    GhostSerializer<YamlCapableDto>,
    GhostYamlSerializer<YamlCapableDto> {
    override val typeName: String = "YamlCapableDto"
    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonWriter,
        value: YamlCapableDto
    ) = Unit

    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter,
        value: YamlCapableDto
    ) = Unit

    override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): YamlCapableDto =
        YamlCapableDto(0)

    override fun deserialize(reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader): YamlCapableDto =
        YamlCapableDto(0)

    override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): YamlCapableDto =
        YamlCapableDto(0)

    override fun serialize(
        writer: com.ghost.serialization.writer.yaml.GhostYamlWriter,
        value: YamlCapableDto
    ) = Unit

    override fun serialize(
        writer: com.ghost.serialization.writer.yaml.GhostYamlFlatWriter,
        value: YamlCapableDto
    ) = Unit

    override fun deserialize(reader: com.ghost.serialization.parser.yaml.GhostYamlFlatReader): YamlCapableDto =
        YamlCapableDto(0)
}
