@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.extensions.readQuotedString as readQuotedStringBytes
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.readQuotedString as readQuotedStringChars
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

object StringSerializer : AbstractGhostSerializer<String>() {
    override val typeName: String get() = TOK.TYPE_NAME_STRING

    override fun deserialize(reader: GhostJsonReader): String {
        return reader.readQuotedString()
    }

    override fun deserialize(reader: GhostJsonFlatReader): String {
        return reader.readQuotedStringBytes()
    }

    override fun deserialize(reader: GhostJsonStringReader): String {
        return reader.readQuotedStringChars()
    }

    override fun serialize(writer: GhostJsonWriter, value: String) {
        writer.value(text = value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: String) {
        writer.value(text = value)
    }
}
