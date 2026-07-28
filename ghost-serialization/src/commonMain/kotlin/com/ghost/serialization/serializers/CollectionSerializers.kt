@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.GhostJsonConstants.CLOSE_ARR
import com.ghost.serialization.parser.common.GhostJsonConstants.CLOSE_OBJ
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.decodeResilient
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.hasNext
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextFloat
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.streaming.readList
import com.ghost.serialization.parser.streaming.readSet
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.hasNext
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.parser.strings.nextFloat
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextLong
import com.ghost.serialization.parser.strings.readList
import com.ghost.serialization.parser.strings.readSet
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter


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
            for (i in 0 until size) {
                itemSerializer.serialize(writer, value[i])
            }
        } else {
            for (item in value) {
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
            for (i in 0 until size) {
                itemSerializer.serialize(writer, value[i])
            }
        } else {
            for (item in value) {
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
            for (i in 0 until size) {
                itemSerializer.serialize(writer, value[i])
            }
        } else {
            for (item in value) {
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

    override fun deserialize(
        reader: GhostJsonStringReader
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
 * Serializer implementation for standard Kotlin [Set] collections.
 * Wire format is a JSON array; decode builds [HashSet] directly without a [List] intermediate.
 */
@OptIn(InternalGhostApi::class)
class SetSerializer<T>(
    private val itemSerializer: GhostSerializer<T>
) : GhostSerializer<Set<T>> {

    override val typeName: String
        get() = "Set<${itemSerializer.typeName}>"

    override fun serialize(
        writer: GhostJsonWriter,
        value: Set<T>
    ) {
        writer.beginArray()
        if (value.isEmpty()) {
            writer.endArray()
            return
        }
        for (item in value) {
            itemSerializer.serialize(writer, item)
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonFlatWriter,
        value: Set<T>
    ) {
        writer.beginArray()
        if (value.isEmpty()) {
            writer.endArray()
            return
        }
        for (item in value) {
            itemSerializer.serialize(writer, item)
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: Set<T>
    ) {
        writer.beginArray()
        if (value.isEmpty()) {
            writer.endArray()
            return
        }
        for (item in value) {
            itemSerializer.serialize(writer, item)
        }
        writer.endArray()
    }

    override fun deserialize(
        reader: GhostJsonReader
    ): Set<T> {
        return if (itemSerializer.isResilient) {
            reader.readSet {
                reader.decodeResilient {
                    itemSerializer.deserialize(reader)
                }
            }.filterNotNull().toSet()
        } else {
            reader.readSet {
                itemSerializer.deserialize(reader)
            }
        }
    }

    override fun deserialize(
        reader: GhostJsonFlatReader
    ): Set<T> {
        return if (itemSerializer.isResilient) {
            reader.readSet {
                reader.decodeResilient {
                    itemSerializer.deserialize(reader)
                }
            }.filterNotNull().toSet()
        } else {
            reader.readSet {
                itemSerializer.deserialize(reader)
            }
        }
    }

    override fun deserialize(
        reader: GhostJsonStringReader
    ): Set<T> {
        return if (itemSerializer.isResilient) {
            reader.readSet {
                reader.decodeResilient {
                    itemSerializer.deserialize(reader)
                }
            }.filterNotNull().toSet()
        } else {
            reader.readSet {
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

    override val typeName: String
        get() =
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

    override fun deserialize(
        reader: GhostJsonStringReader
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
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonFlatWriter,
        value: IntArray
    ) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: IntArray
    ) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
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

    override fun deserialize(
        reader: GhostJsonStringReader
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
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonFlatWriter,
        value: LongArray
    ) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: LongArray
    ) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
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

    override fun deserialize(
        reader: GhostJsonStringReader
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

/**
 * Serializer implementation for primitive [FloatArray].
 */
object FloatArraySerializer : GhostSerializer<FloatArray> {

    override val typeName: String = "FloatArray"

    override fun serialize(writer: GhostJsonWriter, value: FloatArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: FloatArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: FloatArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostJsonReader): FloatArray {
        reader.beginArray()
        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return FloatArray(0)
        }
        val list = ArrayList<Float>()
        while (reader.hasNext()) {
            if (list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextFloat())
        }
        reader.endArray()
        return list.toFloatArray()
    }

    override fun deserialize(reader: GhostJsonFlatReader): FloatArray {
        reader.beginArray()
        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return FloatArray(0)
        }
        val list = ArrayList<Float>()
        while (reader.hasNext()) {
            if (list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextFloat())
        }
        reader.endArray()
        return list.toFloatArray()
    }

    override fun deserialize(reader: GhostJsonStringReader): FloatArray {
        reader.beginArray()
        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return FloatArray(0)
        }
        val list = ArrayList<Float>()
        while (reader.hasNext()) {
            if (list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextFloat())
        }
        reader.endArray()
        return list.toFloatArray()
    }
}

/**
 * Serializer implementation for primitive [DoubleArray].
 */
object DoubleArraySerializer : GhostSerializer<DoubleArray> {

    override val typeName: String = "DoubleArray"

    override fun serialize(writer: GhostJsonWriter, value: DoubleArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: DoubleArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: DoubleArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostJsonReader): DoubleArray {
        reader.beginArray()
        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return DoubleArray(0)
        }
        val list = ArrayList<Double>()
        while (reader.hasNext()) {
            if (list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextDouble())
        }
        reader.endArray()
        return list.toDoubleArray()
    }

    override fun deserialize(reader: GhostJsonFlatReader): DoubleArray {
        reader.beginArray()
        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return DoubleArray(0)
        }
        val list = ArrayList<Double>()
        while (reader.hasNext()) {
            if (list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextDouble())
        }
        reader.endArray()
        return list.toDoubleArray()
    }

    override fun deserialize(reader: GhostJsonStringReader): DoubleArray {
        reader.beginArray()
        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return DoubleArray(0)
        }
        val list = ArrayList<Double>()
        while (reader.hasNext()) {
            if (list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextDouble())
        }
        reader.endArray()
        return list.toDoubleArray()
    }
}

/**
 * Serializer implementation for primitive [BooleanArray].
 */
object BooleanArraySerializer : GhostSerializer<BooleanArray> {

    override val typeName: String = "BooleanArray"

    override fun serialize(writer: GhostJsonWriter, value: BooleanArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: BooleanArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: BooleanArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) {
            writer.value(value[i])
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostJsonReader): BooleanArray {
        reader.beginArray()
        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return BooleanArray(0)
        }
        val list = ArrayList<Boolean>()
        while (reader.hasNext()) {
            if (list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextBoolean())
        }
        reader.endArray()
        return list.toBooleanArray()
    }

    override fun deserialize(reader: GhostJsonFlatReader): BooleanArray {
        reader.beginArray()
        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return BooleanArray(0)
        }
        val list = ArrayList<Boolean>()
        while (reader.hasNext()) {
            if (list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextBoolean())
        }
        reader.endArray()
        return list.toBooleanArray()
    }

    override fun deserialize(reader: GhostJsonStringReader): BooleanArray {
        reader.beginArray()
        if (reader.peekByte() == CLOSE_ARR) {
            reader.endArray()
            return BooleanArray(0)
        }
        val list = ArrayList<Boolean>()
        while (reader.hasNext()) {
            if (list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextBoolean())
        }
        reader.endArray()
        return list.toBooleanArray()
    }
}
