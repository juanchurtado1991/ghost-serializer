@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoBoolValueSerializer : AbstractGhostSerializer<ProtoBoolValue>() {
    override val typeName: String get() = PC.WKT_BOOL_VALUE_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoBoolValue =
        ProtoBoolValue(value = reader.nextBoolean())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoBoolValue =
        ProtoBoolValue(value = reader.nextBoolean())

    override fun deserialize(reader: GhostJsonStringReader): ProtoBoolValue =
        ProtoBoolValue(value = reader.nextBoolean())

    override fun serialize(writer: GhostJsonWriter, value: ProtoBoolValue) {
        writer.value(value = value.value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoBoolValue) {
        writer.value(value = value.value)
    }
}
