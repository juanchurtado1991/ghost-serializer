@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoDoubleValueSerializer : AbstractGhostSerializer<ProtoDoubleValue>() {
    override val typeName: String get() = PC.WKT_DOUBLE_VALUE_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoDoubleValue =
        ProtoDoubleValue(value = reader.nextDouble())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoDoubleValue =
        ProtoDoubleValue(value = reader.nextDouble())

    override fun deserialize(reader: GhostJsonStringReader): ProtoDoubleValue =
        ProtoDoubleValue(value = reader.nextDouble())

    override fun serialize(writer: GhostJsonWriter, value: ProtoDoubleValue) {
        writer.value(number = value.value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoDoubleValue) {
        writer.value(number = value.value)
    }
}
