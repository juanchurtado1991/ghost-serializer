@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

object ByteSerializer : AbstractGhostSerializer<Byte>() {
    override val typeName: String get() = TOK.TYPE_NAME_BYTE

    override fun deserialize(reader: GhostJsonReader): Byte = reader.nextInt().toByte()

    override fun deserialize(reader: GhostJsonFlatReader): Byte = reader.nextInt().toByte()

    override fun deserialize(reader: GhostJsonStringReader): Byte = reader.nextInt().toByte()

    override fun serialize(writer: GhostJsonWriter, value: Byte) {
        writer.value(number = value.toInt())
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Byte) {
        writer.value(number = value.toInt())
    }
}
