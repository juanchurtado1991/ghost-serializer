@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.narrowToUInt
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextLong
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

object UIntSerializer : AbstractGhostSerializer<UInt>() {
    override val typeName: String get() = TOK.TYPE_NAME_UINT

    override fun deserialize(reader: GhostJsonReader): UInt = narrowToUInt(
        value = reader.nextLong()
    ) { reader.throwError(message = it) }

    override fun deserialize(reader: GhostJsonFlatReader): UInt = narrowToUInt(
        value = reader.nextLong()
    ) { reader.throwError(message = it) }

    override fun deserialize(reader: GhostJsonStringReader): UInt = narrowToUInt(
        value = reader.nextLong()
    ) { reader.throwError(message = it) }

    override fun serialize(writer: GhostJsonWriter, value: UInt) {
        writer.value(number = value.toLong())
    }

    override fun serialize(writer: GhostJsonStringWriter, value: UInt) {
        writer.value(number = value.toLong())
    }
}
