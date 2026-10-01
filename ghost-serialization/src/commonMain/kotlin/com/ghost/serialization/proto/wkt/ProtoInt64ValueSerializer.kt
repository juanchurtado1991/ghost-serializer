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
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoInt64ValueSerializer : AbstractGhostSerializer<ProtoInt64Value>() {
    override val typeName: String get() = PC.WKT_INT64_VALUE_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoInt64Value =
        ProtoInt64Value(value = reader.nextLong())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoInt64Value =
        ProtoInt64Value(value = reader.nextLong())

    override fun deserialize(reader: GhostJsonStringReader): ProtoInt64Value =
        ProtoInt64Value(value = reader.nextLong())

    override fun serialize(writer: GhostJsonWriter, value: ProtoInt64Value) {
        writer.value(text = formatLong(value = value.value))
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoInt64Value) {
        writer.value(text = formatLong(value = value.value))
    }
}
