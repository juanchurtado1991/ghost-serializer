@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.extensions.captureRawJsonBytes
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.captureRawJsonBytes
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.captureRawJsonBytes
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.skipValue
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoAnySerializer : AbstractGhostSerializer<ProtoAny>() {
    override val typeName: String get() = PC.WKT_ANY_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoAny {
        reader.beginObject()
        var typeUrl = ""
        var payload = TOK.EMPTY_BYTES
        while (reader.peekNextToken() != TOK.CLOSE_OBJ_INT) {
            val key = reader.nextString()
            reader.consumeKeySeparator()
            when (key) {
                PC.PROTO_TYPE_URL_KEY -> typeUrl = reader.nextString()
                PC.PROTO_VALUE_KEY -> payload = reader.captureRawJsonBytes()
                else -> reader.skipValue()
            }
            if (reader.peekNextToken() == TOK.COMMA_INT) {
                reader.consumeArraySeparator()
            }
        }
        reader.endObject()
        return ProtoAny(typeUrl = typeUrl, value = payload)
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoAny {
        reader.beginObject()
        var typeUrl = ""
        var payload = TOK.EMPTY_BYTES
        while (reader.peekNextToken() != TOK.CLOSE_OBJ_INT) {
            val key = reader.nextString()
            reader.consumeKeySeparator()
            when (key) {
                PC.PROTO_TYPE_URL_KEY -> typeUrl = reader.nextString()
                PC.PROTO_VALUE_KEY -> payload = reader.captureRawJsonBytes()
                else -> reader.skipValue()
            }
            if (reader.peekNextToken() == TOK.COMMA_INT) {
                reader.consumeArraySeparator()
            }
        }
        reader.endObject()
        return ProtoAny(typeUrl = typeUrl, value = payload)
    }

    override fun deserialize(reader: GhostJsonStringReader): ProtoAny {
        reader.beginObject()
        var typeUrl = ""
        var payload = TOK.EMPTY_BYTES
        while (reader.peekNextToken() != TOK.CLOSE_OBJ_INT) {
            val key = reader.nextString()
            reader.consumeKeySeparator()
            when (key) {
                PC.PROTO_TYPE_URL_KEY -> typeUrl = reader.nextString()
                PC.PROTO_VALUE_KEY -> payload = reader.captureRawJsonBytes()
                else -> reader.skipValue()
            }
            if (reader.peekNextToken() == TOK.COMMA_INT) {
                reader.consumeArraySeparator()
            }
        }
        reader.endObject()
        return ProtoAny(typeUrl = typeUrl, value = payload)
    }

    override fun serialize(writer: GhostJsonWriter, value: ProtoAny) {
        writer.beginObject()
        writer.name(key = PC.PROTO_TYPE_URL_KEY).value(text = value.typeUrl)
        if (value.value.isNotEmpty()) {
            writer.name(key = PC.PROTO_VALUE_KEY)
            writer.rawValue(bytes = value.value)
        }
        writer.endObject()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoAny) {
        writer.beginObject()
        writer.name(key = PC.PROTO_TYPE_URL_KEY).value(text = value.typeUrl)
        if (value.value.isNotEmpty()) {
            writer.name(key = PC.PROTO_VALUE_KEY)
            writer.rawValue(bytes = value.value)
        }
        writer.endObject()
    }
}
