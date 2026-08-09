@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.captureRawJsonBytes
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.captureRawJsonBytes
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Serializer for [ProtoAny].
 */
object ProtoAnySerializer : GhostSerializer<ProtoAny> {
    override val typeName: String get() = C.WKT_ANY_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoAny) {
        writer.beginObject()
        writer.name(C.PROTO_TYPE_URL_KEY).value(value.typeUrl)
        if (value.value.isNotEmpty()) {
            writer.name(C.PROTO_VALUE_KEY)
            writer.rawValue(value.value)
        }
        writer.endObject()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoAny) {
        writer.beginObject()
        writer.name(C.PROTO_TYPE_URL_KEY).value(value.typeUrl)
        if (value.value.isNotEmpty()) {
            writer.name(C.PROTO_VALUE_KEY)
            writer.rawValue(value.value)
        }
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): ProtoAny {
        reader.beginObject()
        var typeUrl = ""
        var payload = ByteArray(0)
        while (reader.peekNextToken() != C.CLOSE_OBJ_INT) {
            val key = reader.nextString()
            reader.consumeKeySeparator()
            when (key) {
                C.PROTO_TYPE_URL_KEY -> typeUrl = reader.nextString()
                C.PROTO_VALUE_KEY -> payload = reader.captureRawJsonBytes()
                else -> reader.skipValue()
            }
            if (reader.peekNextToken() == C.COMMA_INT) {
                reader.consumeArraySeparator()
            }
        }
        reader.endObject()
        return ProtoAny(typeUrl, payload)
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoAny {
        reader.beginObject()
        var typeUrl = ""
        var payload = ByteArray(0)
        while (reader.peekNextToken() != C.CLOSE_OBJ_INT) {
            val key = reader.nextString()
            reader.consumeKeySeparator()
            when (key) {
                C.PROTO_TYPE_URL_KEY -> typeUrl = reader.nextString()
                C.PROTO_VALUE_KEY -> payload = reader.captureRawJsonBytes()
                else -> reader.skipValue()
            }
            if (reader.peekNextToken() == C.COMMA_INT) {
                reader.consumeArraySeparator()
            }
        }
        reader.endObject()
        return ProtoAny(typeUrl, payload)
    }
}
