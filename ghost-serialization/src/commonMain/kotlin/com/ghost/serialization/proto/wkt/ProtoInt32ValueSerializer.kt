@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoInt32ValueSerializer : AbstractGhostSerializer<ProtoInt32Value>() {
    override val typeName: String get() = PC.WKT_INT32_VALUE_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoInt32Value =
        ProtoInt32Value(value = reader.nextInt())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoInt32Value =
        ProtoInt32Value(value = reader.nextInt())

    override fun deserialize(reader: GhostJsonStringReader): ProtoInt32Value =
        ProtoInt32Value(value = reader.nextInt())

    override fun serialize(writer: GhostJsonWriter, value: ProtoInt32Value) {
        writer.value(number = value.value.toLong())
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoInt32Value) {
        writer.value(number = value.value.toLong())
    }
}
