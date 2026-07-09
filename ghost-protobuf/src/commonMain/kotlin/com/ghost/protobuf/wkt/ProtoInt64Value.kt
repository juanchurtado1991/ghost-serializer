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
 * Wrapper message for `int64`.
 *
 * The JSON representation for `Int64Value` is JSON string.
 */
@JvmInline value class ProtoInt64Value(val value: Long)

// Helper to format a Long to String zero-allocation.
// Operates in negative space throughout (never negates the full magnitude) so that
// Long.MIN_VALUE round-trips correctly: -Long.MIN_VALUE overflows back to Long.MIN_VALUE
// in two's complement, which previously corrupted the output to "-0" for that boundary value.
internal fun formatLong(value: Long): String {
    val outputBuffer = ByteArray(C.LONG_BUFFER_SIZE)
    var bufferPosition = 0
    val isNegative = value < 0
    if (isNegative) {
        outputBuffer[bufferPosition++] = C.CHAR_HYPHEN.code.toByte()
    }
    var absoluteValue = if (isNegative) value else -value
    var digitCount = 1
    while (absoluteValue <= -C.BASE_TEN) {
        digitCount++
        absoluteValue /= C.BASE_TEN
    }
    var divisor = 1L
    var digitIndex = digitCount - 1
    while (digitIndex > 0) {
        divisor *= C.BASE_TEN
        digitIndex--
    }
    absoluteValue = if (isNegative) value else -value
    while (divisor > 0) {
        val digit = (-(absoluteValue / divisor)).toInt()
        outputBuffer[bufferPosition++] = (digit + C.ZERO_INT).toByte()
        absoluteValue %= divisor
        divisor /= C.BASE_TEN
    }
    return outputBuffer.decodeToString(0, bufferPosition)
}

/**
 * Serializer for [ProtoInt64Value].
 */
object ProtoInt64ValueSerializer : GhostSerializer<ProtoInt64Value> {
    override val typeName: String get() = C.WKT_INT64_VALUE_TYPE
    override fun serialize(writer: GhostJsonWriter, value: ProtoInt64Value) { writer.value(formatLong(value.value)) }
    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoInt64Value) { writer.value(formatLong(value.value)) }
    override fun serialize(writer: GhostJsonStringWriter, value: ProtoInt64Value) { writer.value(formatLong(value.value)) }
    override fun deserialize(reader: GhostJsonReader): ProtoInt64Value = ProtoInt64Value(reader.nextLong())
    override fun deserialize(reader: GhostJsonFlatReader): ProtoInt64Value = ProtoInt64Value(reader.nextLong())
}
