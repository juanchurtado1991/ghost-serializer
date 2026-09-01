@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
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
import com.ghost.serialization.parser.common.GhostJsonConstants as C


object ProtoValueSerializer : GhostSerializer<ProtoValue> {
    override val typeName: String get() = C.WKT_VALUE_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoValue) {
        when (value) {
            is ProtoValue.Null -> writer.nullValue()
            is ProtoValue.Number -> writer.value(value.value)
            is ProtoValue.Str -> writer.value(value.value)
            is ProtoValue.Bool -> writer.value(value.value)
            is ProtoValue.Struct -> {
                writer.beginObject()
                for ((mapKey, mapValue) in value.value) {
                    writer.name(mapKey)
                    serialize(writer, mapValue)
                }
                writer.endObject()
            }

            is ProtoValue.List -> {
                writer.beginArray()
                for (listItem in value.value) {
                    serialize(writer, listItem)
                }
                writer.endArray()
            }
        }
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoValue) {
        when (value) {
            is ProtoValue.Null -> writer.nullValue()
            is ProtoValue.Number -> writer.value(value.value)
            is ProtoValue.Str -> writer.value(value.value)
            is ProtoValue.Bool -> writer.value(value.value)
            is ProtoValue.Struct -> {
                writer.beginObject()
                for ((mapKey, mapValue) in value.value) {
                    writer.name(mapKey)
                    serialize(writer, mapValue)
                }
                writer.endObject()
            }

            is ProtoValue.List -> {
                writer.beginArray()
                for (listItem in value.value) {
                    serialize(writer, listItem)
                }
                writer.endArray()
            }
        }
    }

    override fun deserialize(reader: GhostJsonReader): ProtoValue {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return ProtoValue.Null
        }
        val token = reader.peekNextToken()
        return when (token) {
            C.QUOTE_INT -> ProtoValue.Str(reader.nextString())
            C.TRUE_CHAR_INT, C.FALSE_CHAR_INT -> ProtoValue.Bool(reader.nextBoolean())
            C.OPEN_ARR_INT -> ProtoValue.List(reader.readList { deserialize(reader) })
            C.OPEN_OBJ_INT -> ProtoValue.Struct(
                reader.readMap(
                    { reader.nextString() },
                    { deserialize(reader) })
            )

            else -> ProtoValue.Number(reader.nextDouble())
        }
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoValue {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return ProtoValue.Null
        }
        val token = reader.peekNextToken()
        return when (token) {
            C.QUOTE_INT -> ProtoValue.Str(reader.nextString())
            C.TRUE_CHAR_INT, C.FALSE_CHAR_INT -> ProtoValue.Bool(reader.nextBoolean())
            C.OPEN_ARR_INT -> ProtoValue.List(reader.readList { deserialize(reader) })
            C.OPEN_OBJ_INT -> ProtoValue.Struct(
                reader.readMap(
                    { reader.nextString() },
                    { deserialize(reader) })
            )

            else -> ProtoValue.Number(reader.nextDouble())
        }
    }

    override fun deserialize(reader: GhostJsonStringReader): ProtoValue {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return ProtoValue.Null
        }
        val token = reader.peekNextToken()
        return when (token) {
            C.QUOTE_INT -> ProtoValue.Str(reader.nextString())
            C.TRUE_CHAR_INT, C.FALSE_CHAR_INT -> ProtoValue.Bool(reader.nextBoolean())
            C.OPEN_ARR_INT -> ProtoValue.List(reader.readList { deserialize(reader) })
            C.OPEN_OBJ_INT -> ProtoValue.Struct(
                reader.readMap(
                    { reader.nextString() },
                    { deserialize(reader) })
            )

            else -> ProtoValue.Number(reader.nextDouble())
        }
    }
}
