@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Wrapper message for `double`.
 *
 * The JSON representation for `DoubleValue` is JSON number.
 */
/**
 * Serializer for [ProtoDoubleValue].
 */
object ProtoDoubleValueSerializer : GhostSerializer<ProtoDoubleValue> {
    override val typeName: String get() = C.WKT_DOUBLE_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoDoubleValue) {
        writer.value(value.value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoDoubleValue) {
        writer.value(value.value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoDoubleValue) {
        writer.value(value.value)
    }

    override fun deserialize(reader: GhostJsonReader): ProtoDoubleValue =
        ProtoDoubleValue(reader.nextDouble())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoDoubleValue =
        ProtoDoubleValue(reader.nextDouble())

    override fun deserialize(reader: GhostJsonStringReader): ProtoDoubleValue =
        ProtoDoubleValue(reader.nextDouble())
}
