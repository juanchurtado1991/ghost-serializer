@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextLong
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Built-in serializer for Kotlin [Long] type.
 */
object LongSerializer : GhostSerializer<Long> {
    override val typeName: String get() = C.TYPE_NAME_LONG

    override fun serialize(writer: GhostJsonWriter, value: Long) {
        writer.value(value)
    }


    override fun serialize(writer: GhostJsonStringWriter, value: Long) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Long {
        return reader.nextLong()
    }

    override fun deserialize(reader: GhostJsonFlatReader): Long {
        return reader.nextLong()
    }

    override fun deserialize(reader: GhostJsonStringReader): Long {
        return reader.nextLong()
    }
}
