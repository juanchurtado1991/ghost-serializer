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
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.hasNext
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Serializer implementation for primitive [BooleanArray].
 */
object BooleanArraySerializer : GhostSerializer<BooleanArray> {

    override val typeName: String = C.TYPE_NAME_BOOLEAN_ARRAY

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
        if (reader.peekByte() == C.CLOSE_ARR) {
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
        if (reader.peekByte() == C.CLOSE_ARR) {
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
        if (reader.peekByte() == C.CLOSE_ARR) {
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
