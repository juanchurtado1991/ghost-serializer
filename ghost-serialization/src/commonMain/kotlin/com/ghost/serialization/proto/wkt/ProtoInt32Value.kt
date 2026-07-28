@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import kotlin.jvm.JvmInline
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Wrapper message for `int32`.
 *
 * The JSON representation for `Int32Value` is JSON number.
 */
@JvmInline
value class ProtoInt32Value(val value: Int)

/**
 * Serializer for [ProtoInt32Value].
 */
object ProtoInt32ValueSerializer : GhostSerializer<ProtoInt32Value> {
    override val typeName: String get() = C.WKT_INT32_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoInt32Value) {
        writer.value(value.value.toLong())
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoInt32Value) {
        writer.value(value.value.toLong())
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoInt32Value) {
        writer.value(value.value.toLong())
    }

    override fun deserialize(reader: GhostJsonReader): ProtoInt32Value =
        ProtoInt32Value(reader.nextInt())

    override fun deserialize(reader: GhostJsonFlatReader): ProtoInt32Value =
        ProtoInt32Value(reader.nextInt())
}
