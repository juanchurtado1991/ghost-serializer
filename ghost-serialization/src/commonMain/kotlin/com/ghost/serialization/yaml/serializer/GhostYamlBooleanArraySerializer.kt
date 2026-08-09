package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlBooleanArraySerializer : GhostYamlSerializer<BooleanArray> {
    override fun serialize(writer: GhostYamlWriter, value: BooleanArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: BooleanArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostYamlFlatReader): BooleanArray {
        reader.beginArray()
        val list = ArrayList<Boolean>()
        while (reader.hasNextArrayElement()) {
            list.add(reader.nextBoolean())
        }
        reader.endArray()
        return list.toBooleanArray()
    }
}
