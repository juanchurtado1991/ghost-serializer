@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextLong
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoUInt32ValueSerializer : AbstractGhostSerializer<ProtoUInt32Value>() {
    override val typeName: String get() = PC.WKT_UINT32_VALUE_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoUInt32Value =
        ProtoUInt32Value(value = requireUInt32(value = reader.nextLong()) { reader.throwError(message = it) })

    override fun deserialize(reader: GhostJsonFlatReader): ProtoUInt32Value =
        ProtoUInt32Value(value = requireUInt32(value = reader.nextLong()) { reader.throwError(message = it) })

    override fun deserialize(reader: GhostJsonStringReader): ProtoUInt32Value =
        ProtoUInt32Value(value = requireUInt32(value = reader.nextLong()) { reader.throwError(message = it) })

    override fun serialize(writer: GhostJsonWriter, value: ProtoUInt32Value) {
        writer.value(number = value.value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoUInt32Value) {
        writer.value(number = value.value)
    }
}

private inline fun requireUInt32(
    value: Long,
    throwError: (String) -> Nothing
): Long {
    if (value < 0) throwError(EM.ERR_NEGATIVE_UNSIGNED)
    if (value > PC.PROTO_UINT32_MAX) throwError(PC.ERR_PROTO_UINT32_OVERFLOW)
    return value
}
