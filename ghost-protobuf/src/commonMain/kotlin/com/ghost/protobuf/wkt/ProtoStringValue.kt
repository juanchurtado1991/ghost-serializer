@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C
import kotlin.jvm.JvmInline

/**
 * Wrapper message for `string`.
 *
 * The JSON representation for `StringValue` is JSON string.
 */
@JvmInline value class ProtoStringValue(val value: String)

/**
 * Serializer for [ProtoStringValue].
 */
object ProtoStringValueSerializer : GhostSerializer<ProtoStringValue> {
    override val typeName: String get() = C.WKT_STRING_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoStringValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoStringValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoStringValue) { writer.value(value.value) }
    override fun deserialize(reader: GhostJsonReader): ProtoStringValue = ProtoStringValue(reader.nextString())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoStringValue = ProtoStringValue(reader.nextString())
}
