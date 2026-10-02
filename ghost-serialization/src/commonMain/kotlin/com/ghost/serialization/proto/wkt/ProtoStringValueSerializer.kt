@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoStringValueSerializer : AbstractGhostSerializer<ProtoStringValue>() {
    override val typeName: String get() = PC.WKT_STRING_VALUE_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoStringValue =
        ProtoStringValue(value = reader.nextString())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoStringValue =
        ProtoStringValue(value = reader.nextString())

    override fun deserialize(reader: GhostJsonStringReader): ProtoStringValue =
        ProtoStringValue(value = reader.nextString())

    override fun serialize(writer: GhostJsonWriter, value: ProtoStringValue) {
        writer.value(text = value.value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoStringValue) {
        writer.value(text = value.value)
    }
}
