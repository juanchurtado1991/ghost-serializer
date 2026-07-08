@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.parser.GhostProtoJsonFlatReader
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.captureRawJsonBytes
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.skipValue
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.consumeArraySeparator
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import com.ghost.serialization.parser.GhostJsonConstants as C

// --- Struct ---

typealias ProtoStruct = Map<String, ProtoValue>

object ProtoStructSerializer : GhostSerializer<ProtoStruct> {
    override val typeName: String get() = C.WKT_STRUCT_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoStruct) {
        writer.beginObject()
        for ((k, v) in value) {
            writer.name(k)
            ProtoValueSerializer.serialize(writer, v)
        }
        writer.endObject()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoStruct) {
        writer.beginObject()
        for ((k, v) in value) {
            writer.name(k)
            ProtoValueSerializer.serialize(writer, v)
        }
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): ProtoStruct {
        val map = mutableMapOf<String, ProtoValue>()
        reader.beginObject()
        while (reader.peekNextToken() != C.CLOSE_OBJ_INT) {
            val key = reader.nextString()
            reader.consumeKeySeparator()
            val value = ProtoValueSerializer.deserialize(reader)
            map[key] = value
            if (reader.peekNextToken() == C.COMMA_INT) {
                reader.consumeArraySeparator()
            }
        }
        reader.endObject()
        return map
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoStruct {
        val map = mutableMapOf<String, ProtoValue>()
        reader.beginObject()
        while (reader.peekNextToken() != C.CLOSE_OBJ_INT) {
            val key = reader.nextString()
            reader.consumeKeySeparator()
            val value = ProtoValueSerializer.deserialize(reader)
            map[key] = value
            if (reader.peekNextToken() == C.COMMA_INT) {
                reader.consumeArraySeparator()
            }
        }
        reader.endObject()
        return map
    }
}

// --- Empty ---

object ProtoEmpty

object ProtoEmptySerializer : GhostSerializer<ProtoEmpty> {
    override val typeName: String get() = C.WKT_EMPTY_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoEmpty) {
        writer.beginObject().endObject()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoEmpty) {
        writer.beginObject().endObject()
    }

    override fun deserialize(reader: GhostJsonReader): ProtoEmpty {
        reader.beginObject()
        while (reader.peekNextToken() != C.CLOSE_OBJ_INT) {
            reader.nextString()
            reader.consumeKeySeparator()
            reader.skipValue()
        }
        reader.endObject()
        return ProtoEmpty
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoEmpty {
        reader.beginObject()
        while (reader.peekNextToken() != C.CLOSE_OBJ_INT) {
            reader.nextString()
            reader.consumeKeySeparator()
            reader.skipValue()
        }
        reader.endObject()
        return ProtoEmpty
    }
}

// --- FieldMask ---

data class ProtoFieldMask(val paths: List<String>)

object ProtoFieldMaskSerializer : GhostSerializer<ProtoFieldMask> {
    override val typeName: String get() = C.WKT_FIELDMASK_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoFieldMask) {
        writer.value(formatFieldMask(value))
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoFieldMask) {
        writer.value(formatFieldMask(value))
    }

    override fun deserialize(reader: GhostJsonReader): ProtoFieldMask {
        return parseFieldMask(reader.nextString())
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoFieldMask {
        return parseFieldMask(reader.nextString())
    }
}

internal fun parseFieldMask(str: String): ProtoFieldMask {
    if (str.isEmpty()) return ProtoFieldMask(emptyList())
    val paths = mutableListOf<String>()
    val sb = StringBuilder()
    var i = 0
    val len = str.length
    while (i < len) {
        val c = str[i]
        if (c == C.CHAR_COMMA) {
            paths.add(sb.toString())
            sb.clear()
        } else {
            if (c.isUpperCase()) {
                sb.append(C.CHAR_UNDERSCORE)
                sb.append(c.lowercaseChar())
            } else {
                sb.append(c)
            }
        }
        i++
    }
    if (sb.isNotEmpty()) {
        paths.add(sb.toString())
    }
    return ProtoFieldMask(paths)
}

internal fun formatFieldMask(mask: ProtoFieldMask): String {
    if (mask.paths.isEmpty()) return ""
    val sb = StringBuilder()
    var idx = 0
    val size = mask.paths.size
    while (idx < size) {
        if (idx > 0) sb.append(C.CHAR_COMMA)
        val path = mask.paths[idx]
        var uppercaseNext = false
        var i = 0
        val len = path.length
        while (i < len) {
            val c = path[i]
            if (c == C.CHAR_UNDERSCORE) {
                uppercaseNext = true
            } else {
                if (uppercaseNext) {
                    sb.append(c.uppercaseChar())
                    uppercaseNext = false
                } else {
                    sb.append(c)
                }
            }
            i++
        }
        idx++
    }
    return sb.toString()
}

// --- Any ---

/**
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

    override fun hashCode(): Int = 31 * typeUrl.hashCode() + value.contentHashCode()

    override fun toString(): String =
        "ProtoAny(typeUrl=$typeUrl, value=${value.decodeToString()})"
}

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
