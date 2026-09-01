package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlBooleanArraySerializer : GhostYamlSerializer<BooleanArray> {
    override fun serialize(writer: GhostYamlWriter, value: BooleanArray) {
        writeYamlArrayCore(
            size = value.size,
            beginArray = { writer.beginArray() },
            writeElement = { writer.value(value[it]) },
            endArray = { writer.endArray() },
        )
    }

    override fun deserialize(reader: GhostYamlFlatReader): BooleanArray =
        readYamlArrayCore(
            beginArray = { reader.beginArray() },
            hasNextArrayElement = { reader.hasNextArrayElement() },
            readElement = { reader.nextBoolean() },
            endArray = { reader.endArray() },
        ).toBooleanArray()
}
