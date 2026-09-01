package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlDoubleArraySerializer : GhostYamlSerializer<DoubleArray> {
    override fun serialize(writer: GhostYamlWriter, value: DoubleArray) {
        writeYamlArrayCore(
            size = value.size,
            beginArray = { writer.beginArray() },
            writeElement = { writer.value(value[it]) },
            endArray = { writer.endArray() },
        )
    }

    override fun deserialize(reader: GhostYamlFlatReader): DoubleArray =
        readYamlArrayCore(
            beginArray = { reader.beginArray() },
            hasNextArrayElement = { reader.hasNextArrayElement() },
            readElement = { reader.nextDouble() },
            endArray = { reader.endArray() },
        ).toDoubleArray()
}
