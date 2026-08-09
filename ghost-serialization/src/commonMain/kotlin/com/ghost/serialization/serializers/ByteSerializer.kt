@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Built-in serializer for Kotlin [Byte] type (JSON number).
 */
object ByteSerializer : GhostSerializer<Byte> {
    override val typeName: String get() = C.TYPE_NAME_BYTE

    override fun serialize(writer: GhostJsonWriter, value: Byte) {
        writer.value(value.toInt())
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Byte) {
        writer.value(value.toInt())
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Byte) {
        writer.value(value.toInt())
    }

    override fun deserialize(reader: GhostJsonReader): Byte = reader.nextInt().toByte()

    override fun deserialize(reader: GhostJsonFlatReader): Byte = reader.nextInt().toByte()

    override fun deserialize(reader: GhostJsonStringReader): Byte = reader.nextInt().toByte()
}
