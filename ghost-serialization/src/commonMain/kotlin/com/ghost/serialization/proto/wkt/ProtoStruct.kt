@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter


/**
 * Type alias for `Struct` message.
 *
 * `Struct` represents a structured data value, consisting of fields
 * which map to dynamically typed values.
 */
typealias ProtoStruct = Map<String, ProtoValue>

/**
 * Serializer for [ProtoStruct].
 */
object ProtoStructSerializer : GhostSerializer<ProtoStruct> {
    override val typeName: String get() = C.WKT_STRUCT_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoStruct) {
        writer.beginObject()
        for ((mapKey, mapValue) in value) {
            writer.name(mapKey)
            ProtoValueSerializer.serialize(writer, mapValue)
        }
        writer.endObject()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoStruct) {
        writer.beginObject()
        for ((mapKey, mapValue) in value) {
            writer.name(mapKey)
            ProtoValueSerializer.serialize(writer, mapValue)
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
