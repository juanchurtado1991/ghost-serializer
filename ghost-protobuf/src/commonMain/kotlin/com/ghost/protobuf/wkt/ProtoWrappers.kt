@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.parser.GhostProtoJsonFlatReader
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.decodeBase64String
import com.ghost.serialization.parser.encodeBase64String
import com.ghost.serialization.parser.nextBoolean
import com.ghost.serialization.parser.nextDouble
import com.ghost.serialization.parser.nextFloat
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextLong
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C
import com.ghost.serialization.writer.GhostJsonStringWriter

@JvmInline value class ProtoBoolValue(val value: Boolean)
@JvmInline value class ProtoStringValue(val value: String)
@JvmInline value class ProtoBytesValue(val value: ByteArray)
@JvmInline value class ProtoDoubleValue(val value: Double)
@JvmInline value class ProtoFloatValue(val value: Float)
@JvmInline value class ProtoInt32Value(val value: Int)
@JvmInline value class ProtoInt64Value(val value: Long)
@JvmInline value class ProtoUInt32Value(val value: Long)
@JvmInline value class ProtoUInt64Value(val value: Long)

// Helper to format a Long to String zero-allocation.
// Operates in negative space throughout (never negates the full magnitude) so that
// Long.MIN_VALUE round-trips correctly: -Long.MIN_VALUE overflows back to Long.MIN_VALUE
// in two's complement, which previously corrupted the output to "-0" for that boundary value.
private fun formatLong(value: Long): String {
    val buf = ByteArray(C.LONG_BUFFER_SIZE)
    var pos = 0
    val isNeg = value < 0
    if (isNeg) {
        buf[pos++] = C.CHAR_HYPHEN.code.toByte()
    }
    var temp = if (isNeg) value else -value
    var digitCount = 1
    while (temp <= -C.BASE_TEN) {
        digitCount++
        temp /= C.BASE_TEN
    }
    var divisor = 1L
    var d = digitCount - 1
    while (d > 0) {
        divisor *= C.BASE_TEN
        d--
    }
    temp = if (isNeg) value else -value
    while (divisor > 0) {
        val digit = (-(temp / divisor)).toInt()
        buf[pos++] = (digit + C.ZERO_INT).toByte()
        temp %= divisor
        divisor /= C.BASE_TEN
    }
    return buf.decodeToString(0, pos)
}

// --- BoolValue Serializer ---

object ProtoBoolValueSerializer : GhostSerializer<ProtoBoolValue> {
    override val typeName: String get() = C.WKT_BOOL_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoBoolValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoBoolValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoBoolValue) { writer.value(value.value) }
    override fun deserialize(reader: GhostJsonReader): ProtoBoolValue = ProtoBoolValue(reader.nextBoolean())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoBoolValue = ProtoBoolValue(reader.nextBoolean())
}

// --- StringValue Serializer ---

object ProtoStringValueSerializer : GhostSerializer<ProtoStringValue> {
    override val typeName: String get() = C.WKT_STRING_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoStringValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoStringValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoStringValue) { writer.value(value.value) }
    override fun deserialize(reader: GhostJsonReader): ProtoStringValue = ProtoStringValue(reader.nextString())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoStringValue = ProtoStringValue(reader.nextString())
}

// --- BytesValue Serializer ---

object ProtoBytesValueSerializer : GhostSerializer<ProtoBytesValue> {
    override val typeName: String get() = C.WKT_BYTES_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoBytesValue) {
        writer.value(encodeBase64String(value.value))
    }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoBytesValue) {
        writer.value(encodeBase64String(value.value))
    }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoBytesValue) {
        writer.value(encodeBase64String(value.value))
    }
    // Reader-agnostic: works on any reader flavor via nextString() + shared decoder, rather
    // than requiring the pooled-scratch-buffer fast path on GhostProtoJsonFlatReader
    // specifically. Previously this threw UnsupportedOperationException on GhostJsonReader
    // (streaming) and on plain GhostJsonFlatReader, which was reachable simply by calling
    // Ghost.deserialize/deserializeStreaming instead of GhostProtobuf.deserialize.
    override fun deserialize(reader: GhostJsonReader): ProtoBytesValue =
        ProtoBytesValue(decodeBase64String(reader.nextString()))
    override fun deserialize(reader: GhostJsonFlatReader): ProtoBytesValue {
        if (reader is GhostProtoJsonFlatReader) {
            return ProtoBytesValue(reader.nextProtoBytes())
        }
        return ProtoBytesValue(decodeBase64String(reader.nextString()))
    }
}

// --- DoubleValue Serializer ---

object ProtoDoubleValueSerializer : GhostSerializer<ProtoDoubleValue> {
    override val typeName: String get() = C.WKT_DOUBLE_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoDoubleValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoDoubleValue) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoDoubleValue) { writer.value(value.value) }
    override fun deserialize(reader: GhostJsonReader): ProtoDoubleValue = ProtoDoubleValue(reader.nextDouble())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoDoubleValue = ProtoDoubleValue(reader.nextDouble())
}

// --- FloatValue Serializer ---

object ProtoFloatValueSerializer : GhostSerializer<ProtoFloatValue> {
    override val typeName: String get() = C.WKT_FLOAT_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoFloatValue) { writer.value(value.value.toDouble()) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoFloatValue) { writer.value(value.value.toDouble()) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoFloatValue) { writer.value(value.value.toDouble()) }
    override fun deserialize(reader: GhostJsonReader): ProtoFloatValue = ProtoFloatValue(reader.nextFloat())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoFloatValue = ProtoFloatValue(reader.nextFloat())
}

// --- Int32Value Serializer ---

object ProtoInt32ValueSerializer : GhostSerializer<ProtoInt32Value> {
    override val typeName: String get() = C.WKT_INT32_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoInt32Value) { writer.value(value.value.toLong()) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoInt32Value) { writer.value(value.value.toLong()) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoInt32Value) { writer.value(value.value.toLong()) }
    override fun deserialize(reader: GhostJsonReader): ProtoInt32Value = ProtoInt32Value(reader.nextInt())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoInt32Value = ProtoInt32Value(reader.nextInt())
}

// --- Int64Value Serializer ---

object ProtoInt64ValueSerializer : GhostSerializer<ProtoInt64Value> {
    override val typeName: String get() = C.WKT_INT64_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoInt64Value) { writer.value(formatLong(value.value)) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoInt64Value) { writer.value(formatLong(value.value)) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoInt64Value) { writer.value(formatLong(value.value)) }
    override fun deserialize(reader: GhostJsonReader): ProtoInt64Value = ProtoInt64Value(reader.nextLong())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoInt64Value = ProtoInt64Value(reader.nextLong())
}

// --- UInt32Value Serializer ---

object ProtoUInt32ValueSerializer : GhostSerializer<ProtoUInt32Value> {
    override val typeName: String get() = C.WKT_UINT32_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoUInt32Value) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoUInt32Value) { writer.value(value.value) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoUInt32Value) { writer.value(value.value) }
    override fun deserialize(reader: GhostJsonReader): ProtoUInt32Value = ProtoUInt32Value(reader.nextLong())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoUInt32Value = ProtoUInt32Value(reader.nextLong())
}

// --- UInt64Value Serializer ---

object ProtoUInt64ValueSerializer : GhostSerializer<ProtoUInt64Value> {
    override val typeName: String get() = C.WKT_UINT64_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoUInt64Value) { writer.value(formatLong(value.value)) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoUInt64Value) { writer.value(formatLong(value.value)) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoUInt64Value) { writer.value(formatLong(value.value)) }
    override fun deserialize(reader: GhostJsonReader): ProtoUInt64Value = ProtoUInt64Value(reader.nextLong())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoUInt64Value = ProtoUInt64Value(reader.nextLong())
}
