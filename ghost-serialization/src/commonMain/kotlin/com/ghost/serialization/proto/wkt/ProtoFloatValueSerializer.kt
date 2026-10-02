@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextFloat
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextFloat
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoFloatValueSerializer : AbstractGhostSerializer<ProtoFloatValue>() {
    override val typeName: String get() = PC.WKT_FLOAT_VALUE_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoFloatValue =
        ProtoFloatValue(value = reader.nextFloat())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoFloatValue =
        ProtoFloatValue(value = reader.nextFloat())

    override fun deserialize(reader: GhostJsonStringReader): ProtoFloatValue =
        ProtoFloatValue(value = reader.nextFloat())

    override fun serialize(writer: GhostJsonWriter, value: ProtoFloatValue) {
        writer.value(number = value.value.toDouble())
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoFloatValue) {
        writer.value(number = value.value.toDouble())
    }
}
