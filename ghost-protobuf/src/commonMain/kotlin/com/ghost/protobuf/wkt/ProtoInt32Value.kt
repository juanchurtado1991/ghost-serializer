@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C
import kotlin.jvm.JvmInline

/**
 * Wrapper message for `int32`.
 *
 * The JSON representation for `Int32Value` is JSON number.
 */
@JvmInline value class ProtoInt32Value(val value: Int)

/**
 * Serializer for [ProtoInt32Value].
 */
object ProtoInt32ValueSerializer : GhostSerializer<ProtoInt32Value> {
    override val typeName: String get() = C.WKT_INT32_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoInt32Value) { writer.value(value.value.toLong()) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoInt32Value) { writer.value(value.value.toLong()) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoInt32Value) { writer.value(value.value.toLong()) }
    override fun deserialize(reader: GhostJsonReader): ProtoInt32Value = ProtoInt32Value(reader.nextInt())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoInt32Value = ProtoInt32Value(reader.nextInt())
}
