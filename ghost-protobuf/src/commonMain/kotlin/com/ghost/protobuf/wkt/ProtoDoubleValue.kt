@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextDouble
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C
import kotlin.jvm.JvmInline

/**
 * Wrapper message for `double`.
 *
 * The JSON representation for `DoubleValue` is JSON number.
 */
@JvmInline value class ProtoDoubleValue(val value: Double)

/**
 * Serializer for [ProtoDoubleValue].
 */
object ProtoDoubleValueSerializer : GhostSerializer<ProtoDoubleValue> {
    override val typeName: String get() = C.WKT_DOUBLE_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoDoubleValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoDoubleValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoDoubleValue) { writer.value(value.value) }
    override fun deserialize(reader: GhostJsonReader): ProtoDoubleValue = ProtoDoubleValue(reader.nextDouble())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoDoubleValue = ProtoDoubleValue(reader.nextDouble())
}
