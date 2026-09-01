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
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.hasNext
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Serializer implementation for primitive [IntArray].
 */
object IntArraySerializer : GhostSerializer<IntArray> {

    override val typeName: String = C.TYPE_NAME_INT_ARRAY

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

        if (reader.peekByte() == C.CLOSE_ARR) {
            reader.endArray()
            return IntArray(0)
        }

        val fast = tryFastIntArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(it) },
            setPosition = { reader.position = it; reader.nextTokenByte = C.RESET_TOKEN_BYTE },
        )
        if (fast != null) {
            reader.endArray()
            return fast
        }

        val list = GhostIntList()
        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && !list.isEmpty()) {
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

        if (reader.peekByte() == C.CLOSE_ARR) {
            reader.endArray()
            return IntArray(0)
        }

        val fast = tryFastIntArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(it) },
            setPosition = { reader.position = it; reader.nextTokenByte = C.RESET_TOKEN_BYTE },
        )
        if (fast != null) {
            reader.endArray()
            return fast
        }

        val list = GhostIntList()
        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && !list.isEmpty()) {
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

        if (reader.peekByte() == C.CLOSE_ARR) {
            reader.endArray()
            return IntArray(0)
        }

        val fast = tryFastIntArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(it) },
            setPosition = { reader.position = it; reader.nextTokenByte = C.RESET_TOKEN_BYTE },
        )
        if (fast != null) {
            reader.endArray()
            return fast
        }

        val list = GhostIntList()
        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && !list.isEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(reader.nextInt())
        }

        reader.endArray()
        return list.toArray()
    }
}
