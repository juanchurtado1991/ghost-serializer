@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.proto.*
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * `Any` contains an arbitrary serialized protocol buffer message along with a
 * URL that describes the type of the serialized message.
 *
 * [value] holds the raw JSON bytes of the WKT-style `"value"` sibling key verbatim
 * (e.g. `"123s"` for a packed `Duration`, or a full JSON object for a packed `Struct`).
 * Empty when the wire object had no `"value"` key. This preserves round-tripping without
 * a type registry to resolve [typeUrl] into a concrete message — see docs/wiki for details.
 */
data class ProtoAny(val typeUrl: String, val value: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtoAny) return false
        return typeUrl == other.typeUrl && value.contentEquals(other.value)
    }

    override fun hashCode(): Int =
        C.COLLISION_HASH_MULTIPLIER * typeUrl.hashCode() + value.contentHashCode()

    override fun toString(): String =
        "ProtoAny(typeUrl=$typeUrl, value=${value.decodeToString()})"
}

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
