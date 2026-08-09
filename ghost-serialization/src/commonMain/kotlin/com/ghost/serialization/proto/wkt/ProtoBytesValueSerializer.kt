@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.decodeBase64String
import com.ghost.serialization.parser.common.encodeBase64String
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Wrapper message for `bytes`.
 *
 * The JSON representation for `BytesValue` is JSON string.
 */
/**
 * Serializer for [ProtoBytesValue].
 */
object ProtoBytesValueSerializer : GhostSerializer<ProtoBytesValue> {
    override val typeName: String get() = C.WKT_BYTES_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoBytesValue) {
        writer.value(encodeBase64String(value.value))
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoBytesValue) {
        writer.value(encodeBase64String(value.value))
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoBytesValue) {
        writer.value(encodeBase64String(value.value))
    }

    // Reader-agnostic: works on any reader flavor via nextString() + shared decoder, rather
    // than requiring the pooled-scratch-buffer fast path on GhostProtoJsonFlatReader
    // specifically.
    override fun deserialize(reader: GhostJsonReader): ProtoBytesValue =
        ProtoBytesValue(decodeBase64String(reader.nextString()))

    override fun deserialize(reader: GhostJsonFlatReader): ProtoBytesValue {
        if (reader is GhostProtoJsonFlatReader) {
            return ProtoBytesValue(reader.nextProtoBytes())
        }
        return ProtoBytesValue(decodeBase64String(reader.nextString()))
    }
}
