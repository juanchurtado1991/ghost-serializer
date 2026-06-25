@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_ARR
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_OBJ
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.beginArray
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeArraySeparator
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.decodeResilient
import com.ghost.serialization.parser.endArray
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.hasNext
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextLong
import com.ghost.serialization.parser.readList
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter
import kotlin.collections.RandomAccess

/**
 * Serializer implementation for standard Kotlin [List] collections.
 */
@OptIn(InternalGhostApi::class)
class ListSerializer<T>(
    private val itemSerializer: GhostSerializer<T>
) : GhostSerializer<List<T>> {

    override val typeName: String
        get() = "List<${itemSerializer.typeName}>"

    override fun serialize(
        writer: GhostJsonWriter,
        value: List<T>
    ) {
        writer.beginArray()
        val size = value.size
        if (size == 0) {
            writer.endArray()
            return
        }
        if (value is RandomAccess) {
            var idx = 0
            while (idx < size) {
                itemSerializer.serialize(writer, value[idx])
                idx++
            }
        } else {
            for (item in value) { // non-RandomAccess: Iterator is unavoidable
                itemSerializer.serialize(writer, item)
            }
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonFlatWriter,
        value: List<T>
    ) {
        writer.beginArray()
        val size = value.size
        if (size == 0) {
            writer.endArray()
            return
        }
        if (value is RandomAccess) {
            var idx = 0
            while (idx < size) {
                itemSerializer.serialize(writer, value[idx])
                idx++
            }
        } else {
            for (item in value) { // non-RandomAccess: Iterator is unavoidable
                itemSerializer.serialize(writer, item)
            }
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: List<T>
    ) {
        writer.beginArray()
        val size = value.size
        if (size == 0) {
            writer.endArray()
            return
        }
        if (value is RandomAccess) {
            var idx = 0
            while (idx < size) {
                itemSerializer.serialize(writer, value[idx])
                idx++
            }
        } else {
            for (item in value) { // non-RandomAccess: Iterator is unavoidable
                itemSerializer.serialize(writer, item)
            }
        }
        writer.endArray()
    }

    override fun deserialize(
        reader: GhostJsonReader
    ): List<T> {
        return if (itemSerializer.isResilient) {
            reader.readList {
                reader.decodeResilient {
                    itemSerializer.deserialize(reader)
                }
            }.filterNotNull()
        } else {
            reader.readList {
                itemSerializer.deserialize(reader)
            }
        }
    }

    override fun deserialize(
        reader: GhostJsonFlatReader
    ): List<T> {
        return if (itemSerializer.isResilient) {
            reader.readList {
                reader.decodeResilient {
                    itemSerializer.deserialize(reader)
                }
            }.filterNotNull()
        } else {
            reader.readList {
                itemSerializer.deserialize(reader)
            }
        }
    }
}

/**
 * Serializer implementation for standard Kotlin [Map] collections with String keys.
 */
class MapSerializer<V>(
    private val valueSerializer: GhostSerializer<V>
) : GhostSerializer<Map<String, V>> {

    override val typeName: String get() =
        "Map<String, ${valueSerializer.typeName}>"

    override fun serialize(
        writer: GhostJsonWriter,
        value: Map<String, V>
    ) {
        writer.beginObject()
        for (entry in value.entries) {
            writer.name(entry.key)
            valueSerializer.serialize(writer, entry.value)
        }
        writer.endObject()
    }

    override fun serialize(
        writer: GhostJsonFlatWriter,
        value: Map<String, V>
    ) {
        writer.beginObject()
        for (entry in value.entries) {
            writer.name(entry.key)
            valueSerializer.serialize(writer, entry.value)
        }
        writer.endObject()
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: Map<String, V>
    ) {
        writer.beginObject()
        for (entry in value.entries) {
            writer.name(entry.key)
            valueSerializer.serialize(writer, entry.value)
        }
        writer.endObject()
    }

    override fun deserialize(
        reader: GhostJsonReader
    ): Map<String, V> {
        reader.beginObject()
        if (reader.peekByte() == CLOSE_OBJ) {
            reader.endObject()
            return emptyMap()
        }

        return buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                reader.consumeKeySeparator()
                put(
                    key,
                    valueSerializer.deserialize(reader)
                )
            }
            reader.endObject()
        }
    }

    override fun deserialize(
        reader: GhostJsonFlatReader
    ): Map<String, V> {
        reader.beginObject()
        if (reader.peekByte() == CLOSE_OBJ) {
            reader.endObject()
            return emptyMap()
        }

        return buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                reader.consumeKeySeparator()
                put(
                    key,
                    valueSerializer.deserialize(reader)
                )
            }
            reader.endObject()
        }
    }
}

/**
 * Serializer implementation for primitive [IntArray].
 */
object IntArraySerializer : GhostSerializer<IntArray> {

    override val typeName: String = "IntArray"

    override fun serialize(
        writer: GhostJsonWriter,
        value: IntArray
    ) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonFlatWriter,
        value: IntArray
    ) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: IntArray
    ) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun deserialize(
        reader: GhostJsonReader
    ): IntArray {
        reader.beginArray()

        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return IntArray(0)
        }

        val list = GhostIntList()
        while (reader.hasNext()) {
            if (!list.isEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextInt())
        }

        reader.endArray()
        return list.toArray()
    }

    override fun deserialize(
        reader: GhostJsonFlatReader
    ): IntArray {
        reader.beginArray()

        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return IntArray(0)
        }

        val list = GhostIntList()
        while (reader.hasNext()) {
            if (!list.isEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextInt())
        }

        reader.endArray()
        return list.toArray()
    }
}

/**
 * Serializer implementation for primitive [LongArray].
 */
object LongArraySerializer : GhostSerializer<LongArray> {

    override val typeName: String = "LongArray"

    override fun serialize(
        writer: GhostJsonWriter,
        value: LongArray
    ) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonFlatWriter,
        value: LongArray
    ) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: LongArray
    ) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun deserialize(
        reader: GhostJsonReader
    ): LongArray {
        reader.beginArray()

        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return LongArray(0)
        }

        val list = GhostLongList()
        while (reader.hasNext()) {
            if (!list.isEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextLong())
        }

        reader.endArray()
        return list.toArray()
    }

    override fun deserialize(
        reader: GhostJsonFlatReader
    ): LongArray {
        reader.beginArray()

        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return LongArray(0)
        }

        val list = GhostLongList()
        while (reader.hasNext()) {
            if (!list.isEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextLong())
        }

        reader.endArray()
        return list.toArray()
    }
}
