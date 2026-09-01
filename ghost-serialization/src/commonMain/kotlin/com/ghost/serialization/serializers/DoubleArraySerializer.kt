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
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.hasNext
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Serializer implementation for primitive [DoubleArray].
 */
object DoubleArraySerializer : GhostSerializer<DoubleArray> {

    override val typeName: String = C.TYPE_NAME_DOUBLE_ARRAY

    override fun serialize(writer: GhostJsonWriter, value: DoubleArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) { writer.value(value[i]) }
        writer.endArray()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: DoubleArray) {
        writer.beginArray()
        val size = value.size
        for (i in 0 until size) { writer.value(value[i]) }
        writer.endArray()
    }

    override fun deserialize(reader: GhostJsonReader): DoubleArray {
        reader.beginArray()
        if (reader.peekByte() == C.CLOSE_ARR) {
            reader.endArray()
            return DoubleArray(0)
        }
        val list = ArrayList<Double>()
        val fastSucceeded = tryFastDecimalArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(it) },
            getPosition = { reader.position },
            setPosition = { reader.position = it; reader.nextTokenByte = C.RESET_TOKEN_BYTE },
            addTo = list,
            parseNext = { reader.nextDouble() },
        )
        if (fastSucceeded) {
            reader.endArray()
            return list.toDoubleArray()
        }
        list.clear()

        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && list.isNotEmpty()) { reader.consumeArraySeparator() }
            list.add(reader.nextDouble())
        }
        reader.endArray()
        return list.toDoubleArray()
    }

    override fun deserialize(reader: GhostJsonFlatReader): DoubleArray {
        reader.beginArray()
        if (reader.peekByte() == C.CLOSE_ARR) {
            reader.endArray()
            return DoubleArray(0)
        }
        val list = ArrayList<Double>()
        val fastSucceeded = tryFastDecimalArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(it) },
            getPosition = { reader.position },
            setPosition = { reader.position = it; reader.nextTokenByte = C.RESET_TOKEN_BYTE },
            addTo = list,
            parseNext = { reader.nextDouble() },
        )
        if (fastSucceeded) {
            reader.endArray()
            return list.toDoubleArray()
        }
        list.clear()

        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && list.isNotEmpty()) { reader.consumeArraySeparator() }
            list.add(reader.nextDouble())
        }
        reader.endArray()
        return list.toDoubleArray()
    }

    override fun deserialize(reader: GhostJsonStringReader): DoubleArray {
        reader.beginArray()
        if (reader.peekByte() == C.CLOSE_ARR) {
            reader.endArray()
            return DoubleArray(0)
        }
        val list = ArrayList<Double>()
        val fastSucceeded = tryFastDecimalArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(it) },
            getPosition = { reader.position },
            setPosition = { reader.position = it; reader.nextTokenByte = C.RESET_TOKEN_BYTE },
            addTo = list,
            parseNext = { reader.nextDouble() },
        )
        if (fastSucceeded) {
            reader.endArray()
            return list.toDoubleArray()
        }
        list.clear()

        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && list.isNotEmpty()) { reader.consumeArraySeparator() }
            list.add(reader.nextDouble())
        }
        reader.endArray()
        return list.toDoubleArray()
    }
}
