@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextULong
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextULong
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoUInt64ValueSerializer : AbstractGhostSerializer<ProtoUInt64Value>() {
    override val typeName: String get() = PC.WKT_UINT64_VALUE_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoUInt64Value =
        ProtoUInt64Value(value = reader.nextULong())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoUInt64Value =
        ProtoUInt64Value(value = reader.nextULong())

    override fun deserialize(reader: GhostJsonStringReader): ProtoUInt64Value =
        ProtoUInt64Value(value = reader.nextULong())

    override fun serialize(writer: GhostJsonWriter, value: ProtoUInt64Value) {
        writer.value(text = value.value.toString())
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoUInt64Value) {
        writer.value(text = value.value.toString())
    }
}
