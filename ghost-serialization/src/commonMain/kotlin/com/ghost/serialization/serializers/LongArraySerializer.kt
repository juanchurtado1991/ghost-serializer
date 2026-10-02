@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
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
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Serializer implementation for primitive [LongArray].
 */
object LongArraySerializer : AbstractGhostSerializer<LongArray>() {

    override val typeName: String = TOK.TYPE_NAME_LONG_ARRAY

    override fun deserialize(reader: GhostJsonReader): LongArray {
        reader.beginArray()

        if (reader.peekByte() == TOK.CLOSE_ARR) {
            reader.endArray()
            return LongArray(0)
        }

        val fast = tryFastLongArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(index = it) },
            setPosition = { reader.position = it; reader.nextTokenByte = SCN.RESET_TOKEN_BYTE },
        )
        if (fast != null) {
            reader.endArray()
            return fast
        }

        val list = GhostLongList()
        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && !list.isEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(value = reader.nextLong())
        }

        reader.endArray()
        return list.toArray()
    }

    override fun deserialize(reader: GhostJsonFlatReader): LongArray {
        reader.beginArray()

        if (reader.peekByte() == TOK.CLOSE_ARR) {
            reader.endArray()
            return LongArray(0)
        }

        val fast = tryFastLongArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(index = it) },
            setPosition = { reader.position = it; reader.nextTokenByte = SCN.RESET_TOKEN_BYTE },
        )
        if (fast != null) {
            reader.endArray()
            return fast
        }

        val list = GhostLongList()
        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && !list.isEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(value = reader.nextLong())
        }

        reader.endArray()
        return list.toArray()
    }

    override fun deserialize(reader: GhostJsonStringReader): LongArray {
        reader.beginArray()

        if (reader.peekByte() == TOK.CLOSE_ARR) {
            reader.endArray()
            return LongArray(0)
        }

        val fast = tryFastLongArrayCore(
            startPosition = reader.position,
            limit = reader.limit,
            getByte = { reader.getByte(index = it) },
            setPosition = { reader.position = it; reader.nextTokenByte = SCN.RESET_TOKEN_BYTE },
        )
        if (fast != null) {
            reader.endArray()
            return fast
        }

        val list = GhostLongList()
        val strict = reader.strictMode
        while (reader.hasNext()) {
            if (strict && !list.isEmpty()) {
                reader.consumeArraySeparator()
            }
            list.add(value = reader.nextLong())
        }

        reader.endArray()
        return list.toArray()
    }

    override fun serialize(writer: GhostJsonWriter, value: LongArray) {
        writer.beginArray()
        writeArrayElements(size = value.size) { writer.value(number = value[it]) }
        writer.endArray()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: LongArray) {
        writer.beginArray()
        writeArrayElements(size = value.size) { writer.value(number = value[it]) }
        writer.endArray()
    }
}
