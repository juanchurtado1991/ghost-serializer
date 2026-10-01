package com.ghost.serialization

import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

internal object YamlCapableDtoSerializer :
    AbstractGhostSerializer<YamlCapableDto>(),
    GhostYamlSerializer<YamlCapableDto> {

    override val typeName: String = "YamlCapableDto"

    override fun serialize(
        writer: GhostJsonWriter,
        value: YamlCapableDto
    ) = Unit

    override fun deserialize(reader: GhostJsonReader): YamlCapableDto =
        YamlCapableDto(id = 0)

    override fun deserialize(reader: GhostJsonStringReader): YamlCapableDto =
        YamlCapableDto(id = 0)

    override fun serialize(
        writer: GhostYamlWriter,
        value: YamlCapableDto
    ) = Unit

    override fun deserialize(reader: GhostYamlFlatReader): YamlCapableDto =
        YamlCapableDto(id = 0)
}
