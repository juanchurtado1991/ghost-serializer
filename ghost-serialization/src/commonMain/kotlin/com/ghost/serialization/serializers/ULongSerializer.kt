@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextULong
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextULong
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

object ULongSerializer : AbstractGhostSerializer<ULong>() {
    override val typeName: String get() = TOK.TYPE_NAME_ULONG

    override fun deserialize(reader: GhostJsonReader): ULong = reader.nextULong()

    override fun deserialize(reader: GhostJsonFlatReader): ULong = reader.nextULong()

    override fun deserialize(reader: GhostJsonStringReader): ULong = reader.nextULong()

    override fun serialize(writer: GhostJsonWriter, value: ULong) {
        writer.value(number = value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ULong) {
        writer.value(number = value)
    }
}
