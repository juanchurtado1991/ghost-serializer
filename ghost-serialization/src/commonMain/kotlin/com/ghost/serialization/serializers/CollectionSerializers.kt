@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_ARR
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_OBJ
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.beginArray
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeArraySeparator
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.endArray
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.hasNext
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextLong
import com.ghost.serialization.parser.readList
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter

@Suppress("NOTHING_TO_INLINE")
private inline fun Any?.ignore() {}

@OptIn(InternalGhostApi::class)
class ListSerializer<T>(
    private val itemSerializer: GhostSerializer<T>
) : GhostSerializer<List<T>> {

    override val typeName: String get() = "List<${itemSerializer.typeName}>"

    override fun serialize(writer: GhostJsonWriter, value: List<T>) {
        writer.beginArray().ignore()
        for (item in value) itemSerializer.serialize(writer, item)
        writer.endArray().ignore()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: List<T>) {
        writer.beginArray().ignore()
        for (item in value) itemSerializer.serialize(writer, item)
        writer.endArray().ignore()
    }

    override fun deserialize(reader: GhostJsonReader): List<T> {
        return reader.readList { itemSerializer.deserialize(reader) }
    }
}

class MapSerializer<V>(
    private val valueSerializer: GhostSerializer<V>
) : GhostSerializer<Map<String, V>> {

    override val typeName: String get() =
        "Map<String, ${valueSerializer.typeName}>"

    override fun serialize(writer: GhostJsonWriter, value: Map<String, V>) {
        writer.beginObject().ignore()
        value.forEach { (key, mapValue) ->
            writer.name(key).ignore()
            valueSerializer.serialize(writer, mapValue)
        }
        writer.endObject().ignore()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Map<String, V>) {
        writer.beginObject().ignore()
        value.forEach { (key, mapValue) ->
            writer.name(key).ignore()
            valueSerializer.serialize(writer, mapValue)
        }
        writer.endObject().ignore()
    }

    override fun deserialize(
        reader: GhostJsonReader
    ): Map<String, V> {

        reader.beginObject().ignore()
        if (reader.peekByte() == CLOSE_OBJ) {
            reader.endObject().ignore()
            return emptyMap()
        }

        return buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                reader.consumeKeySeparator()
                put(key, valueSerializer.deserialize(reader))
            }
            reader.endObject().ignore()
        }
    }
}

object IntArraySerializer : GhostSerializer<IntArray> {

    override val typeName: String = "IntArray"

    override fun serialize(writer: GhostJsonWriter, value: IntArray) {
        writer.beginArray().ignore()
        for (item in value) writer.value(item)
        writer.endArray().ignore()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: IntArray) {
        writer.beginArray().ignore()
        for (item in value) writer.value(item)
        writer.endArray().ignore()
    }

    override fun deserialize(reader: GhostJsonReader): IntArray {
        reader.beginArray().ignore()

        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray().ignore()
            return IntArray(0)
        }

        val list = GhostIntList()
        while (reader.hasNext()) {
            if (!list.isEmpty()) reader.consumeArraySeparator()
            list.add(reader.nextInt())
        }

        reader.endArray().ignore()
        return list.toArray()
    }
}

object LongArraySerializer : GhostSerializer<LongArray> {

    override val typeName: String = "LongArray"

    override fun serialize(writer: GhostJsonWriter, value: LongArray) {
        writer.beginArray().ignore()
        for (item in value) writer.value(item)
        writer.endArray().ignore()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: LongArray) {
        writer.beginArray().ignore()
        for (item in value) writer.value(item)
        writer.endArray().ignore()
    }

    override fun deserialize(reader: GhostJsonReader): LongArray {
        reader.beginArray().ignore()

        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray().ignore()
            return LongArray(0)
        }

        val list = GhostLongList()
        while (reader.hasNext()) {
            if (!list.isEmpty()) reader.consumeArraySeparator()
            list.add(reader.nextLong())
        }

        reader.endArray().ignore()
        return list.toArray()
    }
}
