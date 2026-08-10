package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlLongArraySerializer : GhostYamlSerializer<LongArray> {
    override fun serialize(writer: GhostYamlWriter, value: LongArray) {
        writer.beginArray()
        val size = value.size
        var index = 0
        while (index < size) {
            writer.value(value[index])
            index++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: LongArray) {
        writer.beginArray()
        val size = value.size
        var index = 0
        while (index < size) {
            writer.value(value[index])
            index++
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostYamlFlatReader): LongArray {
        reader.beginArray()
        val list = ArrayList<Long>()
        while (reader.hasNextArrayElement()) {
            list.add(reader.nextLong())
        }
        reader.endArray()
        return list.toLongArray()
    }
}
