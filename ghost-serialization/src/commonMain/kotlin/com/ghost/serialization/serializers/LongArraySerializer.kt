@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.hasNext
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.hasNext
import com.ghost.serialization.parser.strings.nextLong
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Serializer implementation for primitive [LongArray].
 */
object LongArraySerializer : GhostSerializer<LongArray> {

    override val typeName: String = C.TYPE_NAME_LONG_ARRAY

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

        if (reader.peekByte() == C.CLOSE_ARR) {
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

        if (reader.peekByte() == C.CLOSE_ARR) {
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

        if (reader.peekByte() == C.CLOSE_ARR) {
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
