@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextFloat
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextFloat
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

object FloatSerializer : AbstractGhostSerializer<Float>() {
    override val typeName: String get() = TOK.TYPE_NAME_FLOAT

    override fun deserialize(reader: GhostJsonReader): Float = reader.nextFloat()

    override fun deserialize(reader: GhostJsonFlatReader): Float = reader.nextFloat()

    override fun deserialize(reader: GhostJsonStringReader): Float = reader.nextFloat()

    override fun serialize(writer: GhostJsonWriter, value: Float) {
        writer.value(number = value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Float) {
        writer.value(number = value)
    }
}
