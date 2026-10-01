package com.ghost.serialization.proto.parser

import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue


class ProtoJsonNumericTest {

    @Test
    fun testNaNAndInfinity() {
        val reader =
            GhostProtoJsonFlatReader(
                rawData = "{\"v1\":\"NaN\",\"v2\":\"Infinity\",\"v3\":\"-Infinity\"}".encodeToByteArray()
            )
        reader.beginObject()
        assertEquals(
            expected = "v1",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertTrue(actual = reader.nextFloat().isNaN())

        reader.consumeArraySeparator()
        assertEquals(
            expected = "v2",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = Float.POSITIVE_INFINITY,
            actual = reader.nextFloat()
        )

        reader.consumeArraySeparator()
        assertEquals(
            expected = "v3",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = Double.NEGATIVE_INFINITY,
            actual = reader.nextDouble()
        )
        reader.endObject()
    }

    @Test
    fun testIntegerStrictValidation() {
        val readerOk = GhostProtoJsonFlatReader(rawData = "{\"v1\":1.0,\"v2\":\"42.0\"}".encodeToByteArray())
        readerOk.beginObject()
        assertEquals(
            expected = "v1",
            actual = readerOk.nextKey()
        ); readerOk.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = readerOk.nextInt()
        )
        readerOk.consumeArraySeparator()
        assertEquals(
            expected = "v2",
            actual = readerOk.nextKey()
        ); readerOk.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = readerOk.nextInt()
        )
        readerOk.endObject()

        val readerErr = GhostProtoJsonFlatReader(rawData = "{\"v1\":1.5}".encodeToByteArray())
        readerErr.beginObject()
        assertEquals(
            expected = "v1",
            actual = readerErr.nextKey()
        ); readerErr.consumeKeySeparator()
        assertFails { readerErr.nextInt() }
    }

    @Test
    fun testBase64Decoding() {
        val reader = GhostProtoJsonFlatReader(rawData = "\"YWJjMTIzIT8kKiYoKSctPUB+\"".encodeToByteArray())
        val decoded = reader.nextProtoBytes()
        assertEquals(
            expected = "abc123!?$*&()'-=@~",
            actual = decoded.decodeToString()
        )
    }

    @Test
    fun testEnumDecoding() {
        val options = JsonReaderOptions.of("UNKNOWN", "FOO", "BAR")
        val readerStr = GhostProtoJsonFlatReader(rawData = "\"BAR\"".encodeToByteArray())
        assertEquals(
            expected = 2,
            actual = readerStr.nextProtoEnum(options = options)
        )

        val readerInt = GhostProtoJsonFlatReader(rawData = "1".encodeToByteArray())
        assertEquals(
            expected = 1,
            actual = readerInt.nextProtoEnum(options = options)
        )
    }

    @Test
    fun nextProtoUInt64_acceptsQuotedMaxValueOnProtoFlatReader() {
        val reader = GhostProtoJsonFlatReader(rawData = "\"18446744073709551615\"".encodeToByteArray())
        assertEquals(
            expected = ULong.MAX_VALUE,
            actual = reader.nextProtoUInt64()
        )
    }

    @Test
    fun nextProtoUInt64_acceptsBareNumberWithinLongRangeOnProtoFlatReader() {
        val reader = GhostProtoJsonFlatReader(rawData = "9223372036854775807".encodeToByteArray())
        assertEquals(
            expected = Long.MAX_VALUE.toULong(),
            actual = reader.nextProtoUInt64()
        )
    }

    @Test
    fun nextProtoUInt64_acceptsQuotedValueOnPlainFlatReader() {
        val reader = GhostJsonFlatReader(rawData = "\"9000000000000000001\"".encodeToByteArray())
        assertEquals(
            expected = 9_000_000_000_000_000_001uL,
            actual = reader.nextProtoUInt64()
        )
    }

    @Test
    fun nextProtoUInt64_zeroLiteralOnPlainFlatReader() {
        val reader = GhostJsonFlatReader(rawData = "0".encodeToByteArray())
        assertEquals(
            expected = 0uL,
            actual = reader.nextProtoUInt64()
        )
    }

    @Test
    fun nextULong_plainJsonFlatReaderMatchesProtoUInt64Behavior() {
        val reader = GhostJsonFlatReader(rawData = "\"18446744073709551615\"".encodeToByteArray())
        assertEquals(
            expected = ULong.MAX_VALUE,
            actual = reader.nextULong()
        )
    }
}
