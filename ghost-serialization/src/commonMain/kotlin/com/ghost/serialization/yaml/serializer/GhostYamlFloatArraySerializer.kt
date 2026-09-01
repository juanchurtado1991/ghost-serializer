package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlFloatArraySerializer : GhostYamlSerializer<FloatArray> {
    override fun serialize(writer: GhostYamlWriter, value: FloatArray) {
        writeYamlArrayCore(
            size = value.size,
            beginArray = { writer.beginArray() },
            writeElement = { writer.value(value[it]) },
            endArray = { writer.endArray() },
        )
    }

    override fun deserialize(reader: GhostYamlFlatReader): FloatArray =
        readYamlArrayCore(
            beginArray = { reader.beginArray() },
            hasNextArrayElement = { reader.hasNextArrayElement() },
            readElement = { reader.nextFloat() },
            endArray = { reader.endArray() },
        ).toFloatArray()
}
