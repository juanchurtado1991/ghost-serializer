@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import kotlin.jvm.JvmInline
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Wrapper message for `uint32`.
 *
 * The JSON representation for `UInt32Value` is JSON number.
 */
@JvmInline
value class ProtoUInt32Value(val value: Long)

/**
 * Serializer for [ProtoUInt32Value].
 */
object ProtoUInt32ValueSerializer : GhostSerializer<ProtoUInt32Value> {
    override val typeName: String get() = C.WKT_UINT32_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoUInt32Value) {
        writer.value(value.value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoUInt32Value) {
        writer.value(value.value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoUInt32Value) {
        writer.value(value.value)
    }

    override fun deserialize(reader: GhostJsonReader): ProtoUInt32Value =
        ProtoUInt32Value(reader.nextLong())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoUInt32Value =
        ProtoUInt32Value(reader.nextLong())
}
