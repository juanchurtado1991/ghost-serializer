@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.extensions.readList
import com.ghost.serialization.parser.bytes.extensions.readMap
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.consumeNull
import com.ghost.serialization.parser.streaming.isNextNullValue
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.readList
import com.ghost.serialization.parser.streaming.readMap
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.consumeNull
import com.ghost.serialization.parser.strings.isNextNullValue
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.readList
import com.ghost.serialization.parser.strings.readMap
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoValueSerializer : AbstractGhostSerializer<ProtoValue>() {
    override val typeName: String get() = PC.WKT_VALUE_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoValue {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return ProtoValue.Null
        }
        val token = reader.peekNextToken()
        return when (token) {
            TOK.QUOTE_INT -> ProtoValue.Str(value = reader.nextString())
            TOK.TRUE_CHAR_INT, TOK.FALSE_CHAR_INT -> ProtoValue.Bool(value = reader.nextBoolean())
            TOK.OPEN_ARR_INT -> ProtoValue.List(value = reader.readList { deserialize(reader = reader) })
            TOK.OPEN_OBJ_INT -> ProtoValue.Struct(
                value = reader.readMap(
                    keyParser = { reader.nextString() },
                    valueParser = { deserialize(reader = reader) }
                )
            )

            else -> ProtoValue.Number(value = reader.nextDouble())
        }
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoValue {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return ProtoValue.Null
        }
        val token = reader.peekNextToken()
        return when (token) {
            TOK.QUOTE_INT -> ProtoValue.Str(value = reader.nextString())
            TOK.TRUE_CHAR_INT, TOK.FALSE_CHAR_INT -> ProtoValue.Bool(value = reader.nextBoolean())
            TOK.OPEN_ARR_INT -> ProtoValue.List(value = reader.readList { deserialize(reader = reader) })
            TOK.OPEN_OBJ_INT -> ProtoValue.Struct(
                value = reader.readMap(
                    keyParser = { reader.nextString() },
                    valueParser = { deserialize(reader = reader) }
                )
            )

            else -> ProtoValue.Number(value = reader.nextDouble())
        }
    }

    override fun deserialize(reader: GhostJsonStringReader): ProtoValue {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return ProtoValue.Null
        }
        val token = reader.peekNextToken()
        return when (token) {
            TOK.QUOTE_INT -> ProtoValue.Str(value = reader.nextString())
            TOK.TRUE_CHAR_INT, TOK.FALSE_CHAR_INT -> ProtoValue.Bool(value = reader.nextBoolean())
            TOK.OPEN_ARR_INT -> ProtoValue.List(value = reader.readList { deserialize(reader = reader) })
            TOK.OPEN_OBJ_INT -> ProtoValue.Struct(
                value = reader.readMap(
                    keyParser = { reader.nextString() },
                    valueParser = { deserialize(reader = reader) }
                )
            )

            else -> ProtoValue.Number(value = reader.nextDouble())
        }
    }

    override fun serialize(writer: GhostJsonWriter, value: ProtoValue) {
        when (value) {
            is ProtoValue.Null -> writer.nullValue()
            is ProtoValue.Number -> writer.value(number = value.value)
            is ProtoValue.Str -> writer.value(text = value.value)
            is ProtoValue.Bool -> writer.value(value = value.value)
            is ProtoValue.Struct -> {
                writer.beginObject()
                for ((mapKey, mapValue) in value.value) {
                    writer.name(key = mapKey)
                    serialize(writer = writer, value = mapValue)
                }
                writer.endObject()
            }

            is ProtoValue.List -> {
                writer.beginArray()
                for (listItem in value.value) {
                    serialize(writer = writer, value = listItem)
                }
                writer.endArray()
            }
        }
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoValue) {
        when (value) {
            is ProtoValue.Null -> writer.nullValue()
            is ProtoValue.Number -> writer.value(number = value.value)
            is ProtoValue.Str -> writer.value(text = value.value)
            is ProtoValue.Bool -> writer.value(value = value.value)
            is ProtoValue.Struct -> {
                writer.beginObject()
                for ((mapKey, mapValue) in value.value) {
                    writer.name(key = mapKey)
                    serialize(writer = writer, value = mapValue)
                }
                writer.endObject()
            }

            is ProtoValue.List -> {
                writer.beginArray()
                for (listItem in value.value) {
                    serialize(writer = writer, value = listItem)
                }
                writer.endArray()
            }
        }
    }
}
