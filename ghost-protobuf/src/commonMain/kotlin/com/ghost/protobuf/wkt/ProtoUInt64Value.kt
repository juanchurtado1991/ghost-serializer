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
 * Full `uint64` range (0 to [ULong.MAX_VALUE], `18446744073709551615`) — [Long] cannot represent
 * values above `Long.MAX_VALUE` (`9223372036854775807`), which is only half of uint64's range.
 *
 * Wrapper message for `uint64`.
 *
 * The JSON representation for `UInt64Value` is JSON string.
 */
@JvmInline value class ProtoUInt64Value(val value: ULong)

/**
 * Serializer for [ProtoUInt64Value].
 */
object ProtoUInt64ValueSerializer : GhostSerializer<ProtoUInt64Value> {
    override val typeName: String get() = C.WKT_UINT64_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoUInt64Value) { writer.value(value.value.toString()) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoUInt64Value) { writer.value(value.value.toString()) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoUInt64Value) { writer.value(value.value.toString()) }

    // Reader-agnostic: the canonical proto3 JSON form for uint64 is always a quoted decimal
    // string (unlike int64, which many producers also emit unquoted within the safe Long
    // range), so nextString().toULong() is correct on every reader flavor without needing
    // GhostProtoJsonFlatReader-specific numeric coercion.
    override fun deserialize(reader: GhostJsonReader): ProtoUInt64Value = ProtoUInt64Value(reader.nextString().toULong())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoUInt64Value = ProtoUInt64Value(reader.nextString().toULong())
}
