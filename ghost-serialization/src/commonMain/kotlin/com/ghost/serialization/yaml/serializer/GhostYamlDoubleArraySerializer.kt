package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlDoubleArraySerializer : GhostYamlSerializer<DoubleArray> {
    override fun serialize(writer: GhostYamlWriter, value: DoubleArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: DoubleArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostYamlFlatReader): DoubleArray {
        reader.beginArray()
        val list = ArrayList<Double>()
        while (reader.hasNextArrayElement()) {
            list.add(reader.nextDouble())
        }
        reader.endArray()
        return list.toDoubleArray()
    }
}
