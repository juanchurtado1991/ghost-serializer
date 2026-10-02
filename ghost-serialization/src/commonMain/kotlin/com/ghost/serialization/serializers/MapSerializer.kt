@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
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
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Serializer implementation for standard Kotlin [Map] collections with String keys.
 */
class MapSerializer<V>(
    private val valueSerializer: GhostSerializer<V>
) : AbstractGhostSerializer<Map<String, V>>() {

    override val typeName: String
        get() = "${TOK.TYPE_NAME_MAP_STRING_PREFIX}${valueSerializer.typeName}${TOK.TYPE_NAME_GENERIC_SUFFIX}"

    override fun deserialize(reader: GhostJsonReader): Map<String, V> {
        reader.beginObject()
        if (reader.peekByte() == TOK.CLOSE_OBJ) {
            reader.endObject()
            return emptyMap()
        }

        return buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                reader.consumeKeySeparator()
                put(key = key, value = valueSerializer.deserialize(reader = reader))
            }
            reader.endObject()
        }
    }

    override fun deserialize(reader: GhostJsonFlatReader): Map<String, V> {
        reader.beginObject()
        if (reader.peekByte() == TOK.CLOSE_OBJ) {
            reader.endObject()
            return emptyMap()
        }

        return buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                reader.consumeKeySeparator()
                put(key = key, value = valueSerializer.deserialize(reader = reader))
            }
            reader.endObject()
        }
    }

    override fun deserialize(reader: GhostJsonStringReader): Map<String, V> {
        reader.beginObject()
        if (reader.peekByte() == TOK.CLOSE_OBJ) {
            reader.endObject()
            return emptyMap()
        }

        return buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                reader.consumeKeySeparator()
                put(key = key, value = valueSerializer.deserialize(reader = reader))
            }
            reader.endObject()
        }
    }

    override fun serialize(writer: GhostJsonWriter, value: Map<String, V>) {
        writer.beginObject()
        for (entry in value.entries) {
            writer.name(key = entry.key)
            valueSerializer.serialize(writer = writer, value = entry.value)
        }
        writer.endObject()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Map<String, V>) {
        writer.beginObject()
        for (entry in value.entries) {
            writer.name(key = entry.key)
            valueSerializer.serialize(writer = writer, value = entry.value)
        }
        writer.endObject()
    }
}
