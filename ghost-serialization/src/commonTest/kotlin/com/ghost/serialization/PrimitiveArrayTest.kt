@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.serializers.IntArraySerializer
import com.ghost.serialization.serializers.LongArraySerializer
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame


class PrimitiveArrayTest {

    @Test
    fun testIntArrayRoundTrip() {
        val original = intArrayOf(1, 2, 3, 42, 0, -1, Int.MAX_VALUE)
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        IntArraySerializer.serialize(writer, original)
        writer.flush()

        val json = buffer.readUtf8()
        assertEquals(
            expected = "[1,2,3,42,0,-1,2147483647]",
            actual = json
        )

        val reader = GhostJsonReader(json.encodeToByteArray())
        val result = IntArraySerializer.deserialize(reader)
        assertContentEquals(
            expected = original,
            actual = result
        )

        val flatResult =
            IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = json.encodeToByteArray()))
        assertContentEquals(
            expected = original,
            actual = flatResult
        )

        val stringResult = IntArraySerializer.deserialize(GhostJsonStringReader(rawData = json))
        assertContentEquals(
            expected = original,
            actual = stringResult
        )
    }

    @Test
    fun testLongArrayRoundTrip() {
        val original = longArrayOf(1L, 2L, 42L, 0L, -1L, Long.MAX_VALUE)
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        LongArraySerializer.serialize(writer, original)
        writer.flush()

        val json = buffer.readUtf8()
        assertEquals(
            expected = "[1,2,42,0,-1,9223372036854775807]",
            actual = json
        )

        val reader = GhostJsonReader(json.encodeToByteArray())
        val result = LongArraySerializer.deserialize(reader)
        assertContentEquals(
            expected = original,
            actual = result
        )

        val flatResult =
            LongArraySerializer.deserialize(GhostJsonFlatReader(rawData = json.encodeToByteArray()))
        assertContentEquals(
            expected = original,
            actual = flatResult
        )

        val stringResult = LongArraySerializer.deserialize(GhostJsonStringReader(rawData = json))
        assertContentEquals(
            expected = original,
            actual = stringResult
        )
    }

    @Test
    fun getSerializerResolvesIntArrayAndRoundTrips() {
        val serializer = Ghost.getSerializer(IntArray::class)
        assertNotNull(actual = serializer)
        assertSame(
            expected = IntArraySerializer,
            actual = serializer
        )

        val original = intArrayOf(1, 2)
        val bytes = Ghost.encodeToBytes(original)
        assertEquals(
            expected = "[1,2]",
            actual = bytes.decodeToString()
        )
        assertContentEquals(
            expected = original,
            actual = Ghost.deserialize<IntArray>(bytes)
        )
    }

    @Test
    fun testEmptyArrays() {
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        IntArraySerializer.serialize(writer, intArrayOf())
        writer.flush()
        assertEquals(
            expected = "[]",
            actual = buffer.readUtf8()
        )

        val reader = GhostJsonReader("[]".encodeToByteArray())
        assertContentEquals(
            expected = intArrayOf(),
            actual = IntArraySerializer.deserialize(reader)
        )
        assertContentEquals(
            expected = intArrayOf(),
            actual = IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = "[]".encodeToByteArray()))
        )
        assertContentEquals(
            expected = intArrayOf(),
            actual = IntArraySerializer.deserialize(GhostJsonStringReader(rawData = "[]"))
        )
    }

    // Covers the compact-array fast path in GhostFastPrimitiveArrayHelpers.kt (tryFastIntArrayCore /
    // tryFastLongArrayCore) across all three reader channels, plus every condition under which it
    // must bail cleanly to the general element-by-element loop.

    @Test
    fun testIntArrayFastPathAllChannelsAgree() {
        val json = "[1,2,3,42,0,-1,999999999,-999999999]"
        val expected = intArrayOf(1, 2, 3, 42, 0, -1, 999999999, -999999999)
        assertContentEquals(
            expected = expected,
            actual = IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = json.encodeToByteArray()))
        )
        assertContentEquals(
            expected = expected,
            actual = IntArraySerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        )
        assertContentEquals(
            expected = expected,
            actual = IntArraySerializer.deserialize(GhostJsonStringReader(rawData = json))
        )
    }

    @Test
    fun testIntArrayFastPathSingleElement() {
        assertContentEquals(
            expected = intArrayOf(7),
            actual = IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = "[7]".encodeToByteArray()))
        )
        assertContentEquals(
            expected = intArrayOf(-7),
            actual = IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = "[-7]".encodeToByteArray()))
        )
    }

    @Test
    fun testIntArrayFastPathTenDigitsFallsBackToOverflowCheckedSlowPath() {
        // 9 digits (INT_SAFE_DIGITS) parses entirely inside the fast path; a 10th digit must
        // bail to the slow path so the existing overflow check (not duplicated here) applies.
        val json = "[999999999,2147483647]"
        assertContentEquals(
            expected = intArrayOf(999999999, Int.MAX_VALUE),
            actual = IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = json.encodeToByteArray()))
        )
        assertFailsWith<GhostJsonException> {
            IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = "[9999999999]".encodeToByteArray()))
        }
    }

    @Test
    fun testIntArrayFastPathIgnoresMaxCollectionSizeLikeTheSlowPathDoes() {
        // IntArraySerializer's general loop never enforces maxCollectionSize (unlike
        // List<T>/readList) — the fast path matches that existing behavior rather than
        // introducing new enforcement the slow path doesn't have.
        val tooMany = (1..6).joinToString(",", "[", "]")
        val reader = GhostJsonFlatReader(rawData = tooMany.encodeToByteArray()).also { it.maxCollectionSize = 5 }
        assertContentEquals(
            expected = intArrayOf(1, 2, 3, 4, 5, 6),
            actual = IntArraySerializer.deserialize(reader)
        )
    }

    @Test
    fun testIntArrayFastPathBailsOnTrailingComma() {
        assertFailsWith<GhostJsonException> {
            IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = "[1,2,]".encodeToByteArray()))
        }
    }

    @Test
    fun testIntArrayFastPathBailsOnWhitespaceAndDecimalCoercion() {
        // Whitespace between elements: falls back to the general loop, which tolerates it.
        assertContentEquals(
            expected = intArrayOf(1, 2, 3),
            actual = IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = "[1, 2, 3]".encodeToByteArray()))
        )
        // A decimal value inside an IntArray: falls back to the general loop's
        // nextDouble().toInt() coercion path.
        assertContentEquals(
            expected = intArrayOf(1, 2, 3),
            actual = IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = "[1,2.0,3]".encodeToByteArray()))
        )
    }

    @Test
    fun testLongArrayFastPathAllChannelsAgree() {
        val json = "[1,2,42,0,-1,999999999999999999,-999999999999999999]"
        val expected = longArrayOf(1, 2, 42, 0, -1, 999999999999999999L, -999999999999999999L)
        assertContentEquals(
            expected = expected,
            actual = LongArraySerializer.deserialize(GhostJsonFlatReader(rawData = json.encodeToByteArray()))
        )
        assertContentEquals(
            expected = expected,
            actual = LongArraySerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        )
        assertContentEquals(
            expected = expected,
            actual = LongArraySerializer.deserialize(GhostJsonStringReader(rawData = json))
        )
    }

    @Test
    fun testLongArrayFastPathNineteenDigitsFallsBackToOverflowCheckedSlowPath() {
        // 18 digits (LONG_SAFE_DIGITS) parses entirely inside the fast path; a 19th digit must
        // bail to the slow path so the existing overflow check applies.
        assertContentEquals(
            expected = longArrayOf(Long.MAX_VALUE),
            actual = LongArraySerializer.deserialize(
                GhostJsonFlatReader(rawData = "[9223372036854775807]".encodeToByteArray())
            )
        )
        assertFailsWith<GhostJsonException> {
            LongArraySerializer.deserialize(GhostJsonFlatReader(rawData = "[99999999999999999999]".encodeToByteArray()))
        }
    }

    @Test
    fun testLargeCompactIntArrayFastPathMatchesSlowPath() {
        // A 1000-element compact array (the realistic accessHistory-style payload the fast
        // path targets) must produce byte-identical results across all three channels.
        val values = IntArray(1000) { it * 7 - 500 }
        val json = values.joinToString(",", "[", "]")
        assertContentEquals(
            expected = values,
            actual = IntArraySerializer.deserialize(GhostJsonFlatReader(rawData = json.encodeToByteArray()))
        )
        assertContentEquals(
            expected = values,
            actual = IntArraySerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        )
        assertContentEquals(
            expected = values,
            actual = IntArraySerializer.deserialize(GhostJsonStringReader(rawData = json))
        )
    }
}
