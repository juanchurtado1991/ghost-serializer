package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlShortArraySerializer : GhostYamlSerializer<ShortArray> {
    override fun deserialize(
        reader: GhostYamlFlatReader
    ): ShortArray = readYamlArrayCore(
        beginArray = { reader.beginArray() },
        hasNextArrayElement = { reader.hasNextArrayElement() },
        readElement = { reader.nextShort() },
        endArray = { reader.endArray() },
    ).toShortArray()

    override fun serialize(writer: GhostYamlWriter, value: ShortArray) {
        writeYamlArrayCore(
            size = value.size,
            beginArray = { writer.beginArray() },
            writeElement = { writer.value(number = value[it].toInt()) },
            endArray = { writer.endArray() },
        )
    }
}
