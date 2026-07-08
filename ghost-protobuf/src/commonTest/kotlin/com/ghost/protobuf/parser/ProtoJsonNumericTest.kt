package com.ghost.protobuf.parser

import com.ghost.serialization.parser.JsonReaderOptions
import com.ghost.serialization.parser.GhostProtoJsonFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFails

class ProtoJsonNumericTest {

    @Test
    fun testNaNAndInfinity() {
        val reader = GhostProtoJsonFlatReader("{\"v1\":\"NaN\",\"v2\":\"Infinity\",\"v3\":\"-Infinity\"}".encodeToByteArray())
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
        // "YWJjMTIzIT8kKiYoKSctPUB+" is standard base64 for "abc123!?$*&()'-=@~"
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
}
