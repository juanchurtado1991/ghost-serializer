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


/**
 * Wrapper message for `string`.
 *
 * The JSON representation for `StringValue` is JSON string.
 */
/**
 * Serializer for [ProtoStringValue].
 */
object ProtoStringValueSerializer : GhostSerializer<ProtoStringValue> {
    override val typeName: String get() = C.WKT_STRING_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoStringValue) {
        writer.value(value.value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoStringValue) {
        writer.value(value.value)
    }

    override fun deserialize(reader: GhostJsonReader): ProtoStringValue =
        ProtoStringValue(reader.nextString())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoStringValue =
        ProtoStringValue(reader.nextString())

    override fun deserialize(reader: GhostJsonStringReader): ProtoStringValue =
        ProtoStringValue(reader.nextString())
}
