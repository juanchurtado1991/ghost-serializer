@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.GhostJsonConstants as C


object ProtoUInt64ValueSerializer : GhostSerializer<ProtoUInt64Value> {
    override val typeName: String get() = C.WKT_UINT64_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoUInt64Value) {
        writer.value(value.value.toString())
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoUInt64Value) {
        writer.value(value.value.toString())
    }

    // Canonical proto3 JSON form for uint64 is always a quoted decimal string (unlike int64,
    // which many producers emit unquoted within the safe Long range), so this needs no
    // reader-specific numeric coercion.
    override fun deserialize(reader: GhostJsonReader): ProtoUInt64Value =
        ProtoUInt64Value(reader.nextString().toULong())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoUInt64Value =
        ProtoUInt64Value(reader.nextString().toULong())

    override fun deserialize(reader: GhostJsonStringReader): ProtoUInt64Value =
        ProtoUInt64Value(reader.nextString().toULong())
}
