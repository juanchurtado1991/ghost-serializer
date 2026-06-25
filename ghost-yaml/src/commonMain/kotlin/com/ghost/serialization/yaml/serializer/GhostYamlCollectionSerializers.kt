package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import com.ghost.serialization.yaml.writer.GhostYamlWriter
import com.ghost.serialization.yaml.writer.GhostYamlFlatWriter

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

object GhostYamlLongArraySerializer : GhostYamlSerializer<LongArray> {
    override fun serialize(writer: GhostYamlWriter, value: LongArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: LongArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
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
