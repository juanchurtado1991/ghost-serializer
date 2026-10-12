package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlCharArraySerializer : GhostYamlSerializer<CharArray> {
    override fun deserialize(
        reader: GhostYamlFlatReader
    ): CharArray = readYamlArrayCore(
        beginArray = { reader.beginArray() },
        hasNextArrayElement = { reader.hasNextArrayElement() },
        readElement = { reader.nextChar() },
        endArray = { reader.endArray() },
    ).toCharArray()

    override fun serialize(writer: GhostYamlWriter, value: CharArray) {
        writeYamlArrayCore(
            size = value.size,
            beginArray = { writer.beginArray() },
            writeElement = { writer.value(value = value[it]) },
            endArray = { writer.endArray() },
        )
    }
}
