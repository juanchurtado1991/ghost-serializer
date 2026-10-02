package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails


class ProtoWktEdgeCasesTest {

    @Test
    fun testTimestampPrecisionAndMath() {
        val ts1 = parseTimestamp(timestampString = "2026-07-07T20:00:00+02:00")
        // 1783447200L = 2026-07-07 18:00:00 UTC
        assertEquals(
            expected = 1783447200L,
            actual = ts1.seconds
        )

        val formatted = formatTimestamp(timestamp = ProtoTimestamp(seconds = 1783447200L, nanos = 125000000))
        assertEquals(
            expected = "2026-07-07T18:00:00.125Z",
            actual = formatted
        )
    }

    @Test
    fun testDurationSignCoherence() {
        val d1 = parseDuration(durationString = "10.500s")
        assertEquals(
            expected = 10L,
            actual = d1.seconds
        )
        assertEquals(
            expected = 500000000,
            actual = d1.nanos
        )

        val d2 = parseDuration(durationString = "-10.500s")
        assertEquals(
            expected = -10L,
            actual = d2.seconds
        )
        assertEquals(
            expected = -500000000,
            actual = d2.nanos
        )

        // seconds/nanos must carry the same sign; the serializer enforces this.
        assertFails { parseDuration(durationString = "-10.500s").copy(nanos = 500000000) }
    }

    @Test
    fun testLongMinValueFormatting() {
        // Regression: negating Long.MIN_VALUE overflows back to Long.MIN_VALUE in two's
        // complement. formatLong/writeLongToBytes must not naively negate the full value —
        // previously this silently corrupted the output to "-0"/"-0s" instead of throwing
        // or producing the correct digits.
        val formattedDuration = formatDuration(duration = ProtoDuration(seconds = Long.MIN_VALUE, nanos = 0))
        assertEquals(
            expected = "-9223372036854775808s",
            actual = formattedDuration
        )

        val flatBuffer = FlatByteArrayWriter(initialCapacity = 64)
        val writer = GhostJsonWriter(flatBuffer)
        ProtoInt64ValueSerializer.serialize(writer, ProtoInt64Value(value = Long.MIN_VALUE))
        assertEquals(
            expected = "\"-9223372036854775808\"",
            actual = flatBuffer.toStringUtf8()
        )
    }

    @Test
    fun testBase64StringEscapes() {
        // YWJjKzEyMw== -> abc+123 (escaped 'Y' to verify unicode escape)
        val readerEscaped = GhostProtoJsonFlatReader(rawData = "\"\\u0059WJjKzEyMw==\"".encodeToByteArray())
        val decoded = readerEscaped.nextProtoBytes()
        assertEquals(
            expected = "abc+123",
            actual = decoded.decodeToString()
        )

        // Standard slashes escaped: YWJj/zEyMw== -> abc[255]123
        val readerSlash = GhostProtoJsonFlatReader(rawData = "\"YWJj\\/zEyMw==\"".encodeToByteArray())
        val decodedSlash = readerSlash.nextProtoBytes()
        assertEquals(
            expected = 7,
            actual = decodedSlash.size
        )
        assertEquals(
            expected = 'a'.code.toByte(),
            actual = decodedSlash[0]
        )
        assertEquals(
            expected = 'b'.code.toByte(),
            actual = decodedSlash[1]
        )
        assertEquals(
            expected = 'c'.code.toByte(),
            actual = decodedSlash[2]
        )
        assertEquals(
            expected = 255.toByte(),
            actual = decodedSlash[3]
        )
        assertEquals(
            expected = '1'.code.toByte(),
            actual = decodedSlash[4]
        )
        assertEquals(
            expected = '2'.code.toByte(),
            actual = decodedSlash[5]
        )
        assertEquals(
            expected = '3'.code.toByte(),
            actual = decodedSlash[6]
        )
    }
}
