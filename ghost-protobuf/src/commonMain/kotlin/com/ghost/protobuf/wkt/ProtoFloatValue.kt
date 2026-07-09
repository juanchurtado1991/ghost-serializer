@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextFloat
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C
import kotlin.jvm.JvmInline

/**
 * Wrapper message for `float`.
 *
 * The JSON representation for `FloatValue` is JSON number.
 */
@JvmInline value class ProtoFloatValue(val value: Float)

/**
 * Serializer for [ProtoFloatValue].
 */
object ProtoFloatValueSerializer : GhostSerializer<ProtoFloatValue> {
    override val typeName: String get() = C.WKT_FLOAT_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoFloatValue) { writer.value(value.value.toDouble()) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoFloatValue) { writer.value(value.value.toDouble()) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoFloatValue) { writer.value(value.value.toDouble()) }
    override fun deserialize(reader: GhostJsonReader): ProtoFloatValue = ProtoFloatValue(reader.nextFloat())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoFloatValue = ProtoFloatValue(reader.nextFloat())
}
