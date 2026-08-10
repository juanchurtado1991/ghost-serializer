package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlFloatArraySerializer : GhostYamlSerializer<FloatArray> {
    override fun serialize(writer: GhostYamlWriter, value: FloatArray) {
        writer.beginArray()
        val size = value.size
        var index = 0
        while (index < size) {
            writer.value(value[index])
            index++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: FloatArray) {
        writer.beginArray()
        val size = value.size
        var index = 0
        while (index < size) {
            writer.value(value[index])
            index++
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostYamlFlatReader): FloatArray {
        reader.beginArray()
        val list = ArrayList<Float>()
        while (reader.hasNextArrayElement()) {
            list.add(reader.nextFloat())
        }
        reader.endArray()
        return list.toFloatArray()
    }
}
