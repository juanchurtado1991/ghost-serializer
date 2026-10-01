package com.ghost.serialization.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the zero-copy slice path ([RawJson.fromBufferSlice]), which aliases parse-buffer
 * regions without materializing [RawJson.bytes]. Full-buffer content takes a different
 * [equals]/[hashCode] fast path than a slice, so both must agree.
 */
class RawJsonSliceEqualityTest {

    @Test
    fun sliceContentEqualsFullBufferWithSameJson() {
        val padded = "XX{\"a\":1}YY".encodeToByteArray()
        val slice = RawJson.fromBufferSlice(
            buffer = padded,
            offset = 2,
            length = 7
        )
        val full = RawJson.fromString(json = """{"a":1}""")

        assertEquals(
            expected = """{"a":1}""",
            actual = slice.decodeToString()
        )
        assertTrue(actual = slice.contentEquals(other = full))
        assertEquals(
            expected = slice,
            actual = full
        )
        assertEquals(
            expected = slice.hashCode(),
            actual = full.hashCode()
        )
    }

    @Test
    fun slicesFromDifferentBuffersAtDifferentOffsetsAreEqual() {
        val bufferA = "AA{\"a\":1}".encodeToByteArray()
        val bufferB = "BBB{\"a\":1}ZZZ".encodeToByteArray()
        val sliceA = RawJson.fromBufferSlice(
            buffer = bufferA,
            offset = 2,
            length = 7
        )
        val sliceB = RawJson.fromBufferSlice(
            buffer = bufferB,
            offset = 3,
            length = 7
        )

        assertTrue(actual = sliceA.contentEquals(other = sliceB))
        assertEquals(
            expected = sliceA,
            actual = sliceB
        )
        assertEquals(
            expected = sliceA.hashCode(),
            actual = sliceB.hashCode()
        )
    }

    @Test
    fun differentContentIsNotEqualRegardlessOfLength() {
        val a = RawJson.fromString(json = """{"a":1}""")
        val b = RawJson.fromString(json = """{"a":2}""")
        val shorter = RawJson.fromString(json = """{"a":1""")

        assertFalse(actual = a.contentEquals(other = b))
        assertFalse(actual = a == b)
        assertFalse(actual = a.contentEquals(other = shorter))
    }

    @Test
    fun contentEqualsAgainstNullOrOtherTypeIsFalse() {
        val a = RawJson.fromString(json = "42")

        assertFalse(actual = a.contentEquals(other = null))
        assertFalse(actual = a.equals(other = "42"))
    }

    @Test
    fun sameInstanceIsEqualToItself() {
        val a = RawJson.fromString(json = """{"a":1}""")

        assertTrue(actual = a.contentEquals(other = a))
        assertEquals(
            expected = a,
            actual = a
        )
    }
}
