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
