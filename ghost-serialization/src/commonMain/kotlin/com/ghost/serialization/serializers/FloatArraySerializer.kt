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
import com.ghost.serialization.parser.streaming.nextFloat
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.hasNext
import com.ghost.serialization.parser.strings.nextFloat
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Serializer implementation for primitive [FloatArray].
 */
object FloatArraySerializer : GhostSerializer<FloatArray> {

    override val typeName: String = C.TYPE_NAME_FLOAT_ARRAY

    override fun serialize(writer: GhostJsonWriter, value: FloatArray) {
        writer.beginArray()
        writeArrayElements(value.size) { writer.value(value[it]) }
        writer.endArray()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: FloatArray) {
        writer.beginArray()
        writeArrayElements(value.size) { writer.value(value[it]) }
        writer.endArray()
    }

    override fun deserialize(reader: GhostJsonReader): FloatArray {
        reader.beginArray()
        if (reader.peekByte() == C.CLOSE_ARR) {
            reader.endArray()
            return FloatArray(0)
        }
        val list = ArrayList<Float>()
        val fastSucceeded = tryFastDecimalArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(it) },
            getPosition = { reader.position },
            setPosition = { reader.position = it; reader.nextTokenByte = C.RESET_TOKEN_BYTE },
            addTo = list,
            parseNext = { reader.nextFloat() },
        )
        if (fastSucceeded) {
            reader.endArray()
            return list.toFloatArray()
        }
        list.clear()

        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextFloat())
        }
        reader.endArray()
        return list.toFloatArray()
    }

    override fun deserialize(reader: GhostJsonFlatReader): FloatArray {
        reader.beginArray()
        if (reader.peekByte() == C.CLOSE_ARR) {
            reader.endArray()
            return FloatArray(0)
        }
        val list = ArrayList<Float>()
        val fastSucceeded = tryFastDecimalArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(it) },
            getPosition = { reader.position },
            setPosition = { reader.position = it; reader.nextTokenByte = C.RESET_TOKEN_BYTE },
            addTo = list,
            parseNext = { reader.nextFloat() },
        )
        if (fastSucceeded) {
            reader.endArray()
            return list.toFloatArray()
        }
        list.clear()

        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextFloat())
        }
        reader.endArray()
        return list.toFloatArray()
    }

    override fun deserialize(reader: GhostJsonStringReader): FloatArray {
        reader.beginArray()
        if (reader.peekByte() == C.CLOSE_ARR) {
            reader.endArray()
            return FloatArray(0)
        }
        val list = ArrayList<Float>()
        val fastSucceeded = tryFastDecimalArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(it) },
            getPosition = { reader.position },
            setPosition = { reader.position = it; reader.nextTokenByte = C.RESET_TOKEN_BYTE },
            addTo = list,
            parseNext = { reader.nextFloat() },
        )
        if (fastSucceeded) {
            reader.endArray()
            return list.toFloatArray()
        }
        list.clear()

        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && list.isNotEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextFloat())
        }
        reader.endArray()
        return list.toFloatArray()
    }
}
