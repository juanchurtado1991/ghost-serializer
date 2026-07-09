@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextBoolean
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C
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
