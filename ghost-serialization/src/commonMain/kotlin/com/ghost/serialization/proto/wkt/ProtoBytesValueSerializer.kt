@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.decodeBase64String
import com.ghost.serialization.parser.common.encodeBase64String
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoBytesValueSerializer : AbstractGhostSerializer<ProtoBytesValue>() {
    override val typeName: String get() = PC.WKT_BYTES_VALUE_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoBytesValue =
        ProtoBytesValue(value = decodeBase64String(value = reader.nextString()))

    // Fast path for GhostProtoJsonFlatReader (pooled scratch buffer); else shared decoder.
    override fun deserialize(reader: GhostJsonFlatReader): ProtoBytesValue {
        if (reader is GhostProtoJsonFlatReader) {
            return ProtoBytesValue(value = reader.nextProtoBytes())
        }
        return ProtoBytesValue(value = decodeBase64String(value = reader.nextString()))
    }

    override fun deserialize(reader: GhostJsonStringReader): ProtoBytesValue =
        ProtoBytesValue(value = decodeBase64String(value = reader.nextString()))

    override fun serialize(writer: GhostJsonWriter, value: ProtoBytesValue) {
        writer.value(text = encodeBase64String(source = value.value))
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoBytesValue) {
        writer.value(text = encodeBase64String(source = value.value))
    }
}
