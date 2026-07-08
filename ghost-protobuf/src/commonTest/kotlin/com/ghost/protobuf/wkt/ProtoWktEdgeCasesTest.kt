package com.ghost.protobuf.wkt

import com.ghost.serialization.parser.GhostProtoJsonFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ProtoWktEdgeCasesTest {

    @Test
    fun testTimestampPrecisionAndMath() {
        // Positive timezone offset
        val ts1 = parseTimestamp("2026-07-07T20:00:00+02:00")
        // Expected epoch seconds for 2026-07-07 18:00:00 UTC
        assertEquals(1783447200L, ts1.seconds)

        // Date formatting round-trip
        val formatted = formatTimestamp(ProtoTimestamp(1783447200L, 125000000))
        assertEquals("2026-07-07T18:00:00.125Z", formatted)
    }

    @Test
    fun testDurationSignCoherence() {
        // Positive ok
        val d1 = parseDuration("10.500s")
        assertEquals(10L, d1.seconds)
        assertEquals(500000000, d1.nanos)

        // Negative ok
        val d2 = parseDuration("-10.500s")
        assertEquals(-10L, d2.seconds)
        assertEquals(-500000000, d2.nanos)

        // Mismatched sign -> fails
        assertFails { parseDuration("-10.500s").copy(nanos = 500000000) } // sign rule check inside serializer
    }

    @Test
    fun testLongMinValueFormatting() {
        // Regression: negating Long.MIN_VALUE overflows back to Long.MIN_VALUE in two's
        // complement. formatLong/writeLongToBytes must not naively negate the full value —
        // previously this silently corrupted the output to "-0"/"-0s" instead of throwing
        // or producing the correct digits.
        val formattedDuration = formatDuration(ProtoDuration(Long.MIN_VALUE, 0))
        assertEquals("-9223372036854775808s", formattedDuration)

        val flatBuffer = com.ghost.serialization.writer.FlatByteArrayWriter(64)
        val writer = com.ghost.serialization.writer.GhostJsonFlatWriter(flatBuffer)
        ProtoInt64ValueSerializer.serialize(writer, ProtoInt64Value(Long.MIN_VALUE))
        assertEquals("\"-9223372036854775808\"", flatBuffer.toStringUtf8())
    }

    @Test
    fun testBase64StringEscapes() {
        // YWJjKzEyMw== -> abc+123 (escaped 'Y' to verify unicode escape)
        val readerEscaped = GhostProtoJsonFlatReader("\"\\u0059WJjKzEyMw==\"".encodeToByteArray())
        val decoded = readerEscaped.nextProtoBytes()
        assertEquals("abc+123", decoded.decodeToString())

        // Standard slashes escaped: YWJj/zEyMw== -> abc[255]123
        val readerSlash = GhostProtoJsonFlatReader("\"YWJj\\/zEyMw==\"".encodeToByteArray())
        val decodedSlash = readerSlash.nextProtoBytes()
        assertEquals(7, decodedSlash.size)
        assertEquals('a'.code.toByte(), decodedSlash[0])
        assertEquals('b'.code.toByte(), decodedSlash[1])
        assertEquals('c'.code.toByte(), decodedSlash[2])
        assertEquals(255.toByte(), decodedSlash[3])
        assertEquals('1'.code.toByte(), decodedSlash[4])
        assertEquals('2'.code.toByte(), decodedSlash[5])
        assertEquals('3'.code.toByte(), decodedSlash[6])
    }
}
