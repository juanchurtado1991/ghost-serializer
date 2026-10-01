@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.extensions.nextChar
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextChar
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextChar
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/** Serializer for [Char], encoded as a length-1 JSON string (not a number). */
object CharSerializer : AbstractGhostSerializer<Char>() {
    override val typeName: String get() = TOK.TYPE_NAME_CHAR

    override fun deserialize(reader: GhostJsonReader): Char = reader.nextChar()

    override fun deserialize(reader: GhostJsonFlatReader): Char = reader.nextChar()

    override fun deserialize(reader: GhostJsonStringReader): Char = reader.nextChar()

    override fun serialize(writer: GhostJsonWriter, value: Char) {
        writer.value(char = value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Char) {
        writer.value(char = value)
    }
}
