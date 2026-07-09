@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextLong
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C
import kotlin.jvm.JvmInline

/**
 * Wrapper message for `uint32`.
 *
 * The JSON representation for `UInt32Value` is JSON number.
 */
@JvmInline value class ProtoUInt32Value(val value: Long)

/**
 * Serializer for [ProtoUInt32Value].
 */
object ProtoUInt32ValueSerializer : GhostSerializer<ProtoUInt32Value> {
    override val typeName: String get() = C.WKT_UINT32_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoUInt32Value) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoUInt32Value) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoUInt32Value) { writer.value(value.value) }
    override fun deserialize(reader: GhostJsonReader): ProtoUInt32Value = ProtoUInt32Value(reader.nextLong())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoUInt32Value = ProtoUInt32Value(reader.nextLong())
}
