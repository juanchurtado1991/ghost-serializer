@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.extensions.decodeResilient
import com.ghost.serialization.parser.bytes.extensions.readSet
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.decodeResilient
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.hasNext
import com.ghost.serialization.parser.streaming.readSet
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.hasNext
import com.ghost.serialization.parser.strings.readSet
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Serializer implementation for standard Kotlin [Set] collections.
 */
class SetSerializer<T>(
    private val itemSerializer: GhostSerializer<T>
) : AbstractGhostSerializer<Set<T>>() {

    override val typeName: String
        get() = "${TOK.TYPE_NAME_SET_PREFIX}${itemSerializer.typeName}${TOK.TYPE_NAME_GENERIC_SUFFIX}"

    override fun deserialize(reader: GhostJsonReader): Set<T> {
        return if (itemSerializer.isResilient) {
            val result = LinkedHashSet<T>()
            reader.beginArray()
            while (reader.hasNext()) {
                val item = reader.decodeResilient {
                    itemSerializer.deserialize(reader = reader)
                }
                if (item != null) result.add(element = item)
            }
            reader.endArray()
            result
        } else {
            reader.readSet {
                itemSerializer.deserialize(reader = reader)
            }
        }
    }

    override fun deserialize(reader: GhostJsonFlatReader): Set<T> {
        return if (itemSerializer.isResilient) {
            val result = LinkedHashSet<T>()
            reader.beginArray()
            while (reader.hasNext()) {
                val item = reader.decodeResilient {
                    itemSerializer.deserialize(reader = reader)
                }
                if (item != null) result.add(element = item)
            }
            reader.endArray()
            result
        } else {
            reader.readSet {
                itemSerializer.deserialize(reader = reader)
            }
        }
    }

    override fun deserialize(reader: GhostJsonStringReader): Set<T> {
        return if (itemSerializer.isResilient) {
            val result = LinkedHashSet<T>()
            reader.beginArray()
            while (reader.hasNext()) {
                val item = reader.decodeResilient {
                    itemSerializer.deserialize(reader = reader)
                }
                if (item != null) result.add(element = item)
            }
            reader.endArray()
            result
        } else {
            reader.readSet {
                itemSerializer.deserialize(reader = reader)
            }
        }
    }

    override fun serialize(writer: GhostJsonWriter, value: Set<T>) {
        writer.beginArray()
        if (value.isEmpty()) {
            writer.endArray()
            return
        }
        for (item in value) {
            itemSerializer.serialize(writer = writer, value = item)
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Set<T>) {
        writer.beginArray()
        if (value.isEmpty()) {
            writer.endArray()
            return
        }
        for (item in value) {
            itemSerializer.serialize(writer = writer, value = item)
        }
        writer.endArray()
    }
}
