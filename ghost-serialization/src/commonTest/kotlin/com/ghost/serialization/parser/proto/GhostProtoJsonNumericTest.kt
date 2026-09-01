package com.ghost.serialization.proto.parser

import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue


class ProtoJsonNumericTest {

    @Test
    fun testNaNAndInfinity() {
        val reader =
            GhostProtoJsonFlatReader("{\"v1\":\"NaN\",\"v2\":\"Infinity\",\"v3\":\"-Infinity\"}".encodeToByteArray())
        reader.beginObject()
        assertEquals("v1", reader.nextKey())
        reader.consumeKeySeparator()
        assertTrue(reader.nextFloat().isNaN())

        reader.consumeArraySeparator()
        assertEquals("v2", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(Float.POSITIVE_INFINITY, reader.nextFloat())

        reader.consumeArraySeparator()
        assertEquals("v3", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(Double.NEGATIVE_INFINITY, reader.nextDouble())
        reader.endObject()
    }

    @Test
    fun testIntegerStrictValidation() {
        val readerOk = GhostProtoJsonFlatReader("{\"v1\":1.0,\"v2\":\"42.0\"}".encodeToByteArray())
        readerOk.beginObject()
        assertEquals("v1", readerOk.nextKey()); readerOk.consumeKeySeparator()
        assertEquals(1, readerOk.nextInt())
        readerOk.consumeArraySeparator()
        assertEquals("v2", readerOk.nextKey()); readerOk.consumeKeySeparator()
        assertEquals(42, readerOk.nextInt())
        readerOk.endObject()

        val readerErr = GhostProtoJsonFlatReader("{\"v1\":1.5}".encodeToByteArray())
        readerErr.beginObject()
        assertEquals("v1", readerErr.nextKey()); readerErr.consumeKeySeparator()
        assertFails { readerErr.nextInt() }
    }

    @Test
    fun testBase64Decoding() {
        val reader = GhostProtoJsonFlatReader("\"YWJjMTIzIT8kKiYoKSctPUB+\"".encodeToByteArray())
        val decoded = reader.nextProtoBytes()
        assertEquals("abc123!?$*&()'-=@~", decoded.decodeToString())
    }

    @Test
    fun testEnumDecoding() {
        val options = JsonReaderOptions.of("UNKNOWN", "FOO", "BAR")
        val readerStr = GhostProtoJsonFlatReader("\"BAR\"".encodeToByteArray())
        assertEquals(2, readerStr.nextProtoEnum(options))

        val readerInt = GhostProtoJsonFlatReader("1".encodeToByteArray())
        assertEquals(1, readerInt.nextProtoEnum(options))
    }

    @Test
    fun nextProtoUInt64_acceptsQuotedMaxValueOnProtoFlatReader() {
        val reader = GhostProtoJsonFlatReader("\"18446744073709551615\"".encodeToByteArray())
        assertEquals(ULong.MAX_VALUE, reader.nextProtoUInt64())
    }

    @Test
    fun nextProtoUInt64_acceptsBareNumberWithinLongRangeOnProtoFlatReader() {
        val reader = GhostProtoJsonFlatReader("9223372036854775807".encodeToByteArray())
        assertEquals(Long.MAX_VALUE.toULong(), reader.nextProtoUInt64())
    }

    @Test
    fun nextProtoUInt64_acceptsQuotedValueOnPlainFlatReader() {
        val reader = GhostJsonFlatReader("\"9000000000000000001\"".encodeToByteArray())
        assertEquals(9_000_000_000_000_000_001uL, reader.nextProtoUInt64())
    }

    @Test
    fun nextProtoUInt64_zeroLiteralOnPlainFlatReader() {
        val reader = GhostJsonFlatReader("0".encodeToByteArray())
        assertEquals(0uL, reader.nextProtoUInt64())
    }

    @Test
    fun nextULong_plainJsonFlatReaderMatchesProtoUInt64Behavior() {
        val reader = GhostJsonFlatReader("\"18446744073709551615\"".encodeToByteArray())
        assertEquals(ULong.MAX_VALUE, reader.nextULong())
    }
}
