@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.readQuotedString
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Built-in serializer for Kotlin [String] type.
 */
object StringSerializer : GhostSerializer<String> {
    override val typeName: String get() = C.TYPE_NAME_STRING

    override fun serialize(writer: GhostJsonWriter, value: String) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: String) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): String {
        return reader.readQuotedString()
    }

    override fun deserialize(reader: GhostJsonFlatReader): String {
        return reader.readQuotedString()
    }

    override fun deserialize(reader: GhostJsonStringReader): String {
        return reader.readQuotedString()
    }
}
