@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.nextChar
import com.ghost.serialization.parser.bytes.readQuotedString
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextChar
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextFloat
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.parser.strings.nextChar
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.parser.strings.nextFloat
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextLong
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter


/**
 * Built-in serializer for Kotlin [Char] type (JSON string of length 1).
 */
object CharSerializer : GhostSerializer<Char> {
    override val typeName: String get() = "Char"

    override fun serialize(writer: GhostJsonWriter, value: Char) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Char) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Char) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Char = reader.nextChar()

    override fun deserialize(reader: GhostJsonFlatReader): Char = reader.nextChar()

    override fun deserialize(reader: GhostJsonStringReader): Char = reader.nextChar()
}
