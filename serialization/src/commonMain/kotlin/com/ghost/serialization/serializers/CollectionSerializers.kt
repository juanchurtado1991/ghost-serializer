package com.ghost.serialization.serializers

import com.ghost.serialization.core.contract.GhostSerializer
import com.ghost.serialization.core.parser.GhostJsonConstants
import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.parser.consumeArraySeparator
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.parser.nextKey
import com.ghost.serialization.core.parser.nextLong
import com.ghost.serialization.core.parser.readList
import com.ghost.serialization.core.writer.GhostJsonWriter

class ListSerializer<T>(
    private val itemSerializer: GhostSerializer<T>
) : GhostSerializer<List<T>> {
    override fun serialize(writer: GhostJsonWriter, value: List<T>) {
        writer.beginArray()
        for (i in 0 until value.size) {
            itemSerializer.serialize(writer, value[i])
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostJsonReader): List<T> {
        return reader.readList { itemSerializer.deserialize(reader) }
    }
}

class MapSerializer<V>(
    private val valueSerializer: GhostSerializer<V>
) : GhostSerializer<Map<String, V>> {
    override fun serialize(
        writer: GhostJsonWriter,
        value: Map<String, V>
    ) {
        writer.beginObject()
        value.forEach { (k, v) ->
            writer.name(k)
            valueSerializer.serialize(writer, v)
        }
        writer.endObject()
    }

    override fun deserialize(
        reader: GhostJsonReader
    ): Map<String, V> {
        reader.beginObject()
        if (reader.peekByte() == GhostJsonConstants.CLOSE_OBJ) {
            reader.endObject(); return emptyMap()
        }
        return buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                put(key, valueSerializer.deserialize(reader))
            }
            reader.endObject()
        }
    }
}

object IntArraySerializer : GhostSerializer<IntArray> {
    override fun serialize(
        writer: GhostJsonWriter,
        value: IntArray
    ) {
        writer.beginArray()
        for (i in 0 until value.size) {
            writer.value(value[i].toLong())
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostJsonReader): IntArray {
        reader.beginArray()
        if (reader.peekByte() == GhostJsonConstants.CLOSE_ARR) {
            reader.endArray()
            return IntArray(0)
        }
        val list = GhostIntList()
        while (reader.hasNext()) {
            if (!list.isEmpty()) reader.consumeArraySeparator()
            list.add(reader.nextInt())
        }
        reader.endArray()
        return list.toArray()
    }
}

object LongArraySerializer : GhostSerializer<LongArray> {
    override fun serialize(
        writer: GhostJsonWriter,
        value: LongArray
    ) {
        writer.beginArray()
        for (i in 0 until value.size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostJsonReader): LongArray {
        reader.beginArray()
        if (reader.peekByte() == GhostJsonConstants.CLOSE_ARR) {
            reader.endArray()
            return LongArray(0)
        }
        val list = GhostLongList()
        while (reader.hasNext()) {
            if (!list.isEmpty()) reader.consumeArraySeparator()
            list.add(reader.nextLong())
        }
        reader.endArray()
        return list.toArray()
    }
}
