@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.GhostJsonConstants.CLOSE_ARR
import com.ghost.serialization.parser.common.GhostJsonConstants.CLOSE_OBJ
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.decodeResilient
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.hasNext
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextFloat
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.streaming.readList
import com.ghost.serialization.parser.streaming.readSet
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.hasNext
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.parser.strings.nextFloat
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextLong
import com.ghost.serialization.parser.strings.readList
import com.ghost.serialization.parser.strings.readSet
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter


/**
 * Serializer implementation for standard Kotlin [List] collections.
 */
/**
 * Serializer implementation for standard Kotlin [Map] collections with String keys.
 */
class MapSerializer<V>(
    private val valueSerializer: GhostSerializer<V>
) : GhostSerializer<Map<String, V>> {

    override val typeName: String
        get() =
            "Map<String, ${valueSerializer.typeName}>"

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
        writer: GhostJsonFlatWriter,
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
        if (reader.peekByte() == CLOSE_OBJ) {
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
        if (reader.peekByte() == CLOSE_OBJ) {
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
        if (reader.peekByte() == CLOSE_OBJ) {
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
