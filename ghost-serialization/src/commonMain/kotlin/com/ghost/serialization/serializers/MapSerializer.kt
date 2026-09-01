@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Serializer implementation for standard Kotlin [Map] collections with String keys.
 */
class MapSerializer<V>(
    private val valueSerializer: GhostSerializer<V>
) : GhostSerializer<Map<String, V>> {

    override val typeName: String
        get() =
            "${C.TYPE_NAME_MAP_STRING_PREFIX}${valueSerializer.typeName}${C.TYPE_NAME_GENERIC_SUFFIX}"

    override fun serialize(
        writer: GhostJsonWriter,
        value: Map<String, V>
    ) {
        writer.beginObject()
        for (entry in value.entries) {
            writer.name(entry.key)
            valueSerializer.serialize(writer, entry.value)
        }
        writer.endObject()
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: Map<String, V>
    ) {
        writer.beginObject()
        for (entry in value.entries) {
            writer.name(entry.key)
            valueSerializer.serialize(writer, entry.value)
        }
        writer.endObject()
    }

    override fun deserialize(
        reader: GhostJsonReader
    ): Map<String, V> {
        reader.beginObject()
        if (reader.peekByte() == C.CLOSE_OBJ) {
            reader.endObject()
            return emptyMap()
        }

        return buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                reader.consumeKeySeparator()
                put(
                    key,
                    valueSerializer.deserialize(reader)
                )
            }
            reader.endObject()
        }
    }

    override fun deserialize(
        reader: GhostJsonFlatReader
    ): Map<String, V> {
        reader.beginObject()
        if (reader.peekByte() == C.CLOSE_OBJ) {
            reader.endObject()
            return emptyMap()
        }

        return buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                reader.consumeKeySeparator()
                put(
                    key,
                    valueSerializer.deserialize(reader)
                )
            }
            reader.endObject()
        }
    }

    override fun deserialize(
        reader: GhostJsonStringReader
    ): Map<String, V> {
        reader.beginObject()
        if (reader.peekByte() == C.CLOSE_OBJ) {
            reader.endObject()
            return emptyMap()
        }

        return buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                reader.consumeKeySeparator()
                put(
                    key,
                    valueSerializer.deserialize(reader)
                )
            }
            reader.endObject()
        }
    }
}
