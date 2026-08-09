package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlIntArraySerializer : GhostYamlSerializer<IntArray> {
    override fun serialize(writer: GhostYamlWriter, value: IntArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: IntArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostYamlFlatReader): IntArray {
        reader.beginArray()
        val list = ArrayList<Int>()
        while (reader.hasNextArrayElement()) {
            list.add(reader.nextInt())
        }
        reader.endArray()
        return list.toIntArray()
    }
}
