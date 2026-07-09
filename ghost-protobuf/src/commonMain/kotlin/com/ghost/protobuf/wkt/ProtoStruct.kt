@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeArraySeparator
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C

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
