@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.narrowToUShort
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

object UShortSerializer : AbstractGhostSerializer<UShort>() {
    override val typeName: String get() = TOK.TYPE_NAME_USHORT

    override fun deserialize(reader: GhostJsonReader): UShort = narrowToUShort(
        value = reader.nextInt()
    ) { reader.throwError(message = it) }

    override fun deserialize(reader: GhostJsonFlatReader): UShort = narrowToUShort(
        value = reader.nextInt()
    ) { reader.throwError(message = it) }

    override fun deserialize(reader: GhostJsonStringReader): UShort = narrowToUShort(
        value = reader.nextInt()
    ) { reader.throwError(message = it) }

    override fun serialize(writer: GhostJsonWriter, value: UShort) {
        writer.value(number = value.toInt())
    }

    override fun serialize(writer: GhostJsonStringWriter, value: UShort) {
        writer.value(number = value.toInt())
    }
}
