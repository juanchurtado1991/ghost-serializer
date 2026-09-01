package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlLongArraySerializer : GhostYamlSerializer<LongArray> {
    override fun serialize(writer: GhostYamlWriter, value: LongArray) {
        writeYamlArrayCore(
            size = value.size,
            beginArray = { writer.beginArray() },
            writeElement = { writer.value(value[it]) },
            endArray = { writer.endArray() },
        )
    }

    override fun deserialize(reader: GhostYamlFlatReader): LongArray =
        readYamlArrayCore(
            beginArray = { reader.beginArray() },
            hasNextArrayElement = { reader.hasNextArrayElement() },
            readElement = { reader.nextLong() },
            endArray = { reader.endArray() },
        ).toLongArray()
}
