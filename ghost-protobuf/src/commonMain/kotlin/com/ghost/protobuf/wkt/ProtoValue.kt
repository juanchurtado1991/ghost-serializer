@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.consumeNull
import com.ghost.serialization.parser.isNextNullValue
import com.ghost.serialization.parser.nextBoolean
import com.ghost.serialization.parser.nextDouble
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.readList
import com.ghost.serialization.parser.readMap
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C

sealed class ProtoValue {
    object Null : ProtoValue()
    data class Number(val value: Double) : ProtoValue()
    data class Str(val value: String) : ProtoValue()
    data class Bool(val value: Boolean) : ProtoValue()
    data class Struct(val value: Map<String, ProtoValue>) : ProtoValue()
    data class List(val value: kotlin.collections.List<ProtoValue>) : ProtoValue()
}

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

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoValue) {
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
}
