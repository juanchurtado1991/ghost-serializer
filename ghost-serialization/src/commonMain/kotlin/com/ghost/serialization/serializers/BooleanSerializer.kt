@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Built-in serializer for Kotlin [Boolean] type.
 */
object BooleanSerializer : GhostSerializer<Boolean> {
    override val typeName: String get() = C.TYPE_NAME_BOOLEAN

    override fun serialize(writer: GhostJsonWriter, value: Boolean) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Boolean) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Boolean {
        return reader.nextBoolean()
    }

    override fun deserialize(reader: GhostJsonFlatReader): Boolean {
        return reader.nextBoolean()
    }

    override fun deserialize(reader: GhostJsonStringReader): Boolean {
        return reader.nextBoolean()
    }
}
