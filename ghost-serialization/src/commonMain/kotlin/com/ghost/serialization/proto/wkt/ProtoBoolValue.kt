@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import kotlin.jvm.JvmInline


/**
 * Wrapper message for `bool`.
 *
 * The JSON representation for `BoolValue` is JSON boolean.
 */
@JvmInline value class ProtoBoolValue(val value: Boolean)

/**
 * Serializer for [ProtoBoolValue].
 */
object ProtoBoolValueSerializer : GhostSerializer<ProtoBoolValue> {
    override val typeName: String get() = C.WKT_BOOL_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoBoolValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoBoolValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoBoolValue) { writer.value(value.value) }
    override fun deserialize(reader: GhostJsonReader): ProtoBoolValue = ProtoBoolValue(reader.nextBoolean())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoBoolValue = ProtoBoolValue(reader.nextBoolean())
}
