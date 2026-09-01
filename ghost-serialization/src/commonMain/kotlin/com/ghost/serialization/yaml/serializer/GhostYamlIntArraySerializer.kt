package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlIntArraySerializer : GhostYamlSerializer<IntArray> {
    override fun serialize(writer: GhostYamlWriter, value: IntArray) {
        writeYamlArrayCore(
            size = value.size,
            beginArray = { writer.beginArray() },
            writeElement = { writer.value(value[it]) },
            endArray = { writer.endArray() },
        )
    }

    override fun deserialize(reader: GhostYamlFlatReader): IntArray =
        readYamlArrayCore(
            beginArray = { reader.beginArray() },
            hasNextArrayElement = { reader.hasNextArrayElement() },
            readElement = { reader.nextInt() },
            endArray = { reader.endArray() },
        ).toIntArray()
}
