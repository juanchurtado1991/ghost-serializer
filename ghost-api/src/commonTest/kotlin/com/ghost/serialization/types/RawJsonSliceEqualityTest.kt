package com.ghost.serialization.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the zero-copy slice path ([RawJson.fromBufferSlice]) that the flat-byte reader uses
 * to alias parse-buffer regions without materializing [RawJson.bytes]. Full-buffer content
 * (offset 0, length == storage.size) takes a different [equals]/[hashCode] fast path than a
 * slice into a larger buffer, so both need to agree.
 */
class RawJsonSliceEqualityTest {

    @Test
    fun sliceContentEqualsFullBufferWithSameJson() {
        val padded = "XX{\"a\":1}YY".encodeToByteArray()
        val slice = RawJson.fromBufferSlice(padded, 2, 7)
        val full = RawJson.fromString("""{"a":1}""")

        assertEquals("""{"a":1}""", slice.decodeToString())
        assertTrue(slice.contentEquals(full))
        assertEquals(slice, full)
        assertEquals(slice.hashCode(), full.hashCode())
    }

    @Test
    fun slicesFromDifferentBuffersAtDifferentOffsetsAreEqual() {
        val bufferA = "AA{\"a\":1}".encodeToByteArray()
        val bufferB = "BBB{\"a\":1}ZZZ".encodeToByteArray()
        val sliceA = RawJson.fromBufferSlice(bufferA, 2, 7)
        val sliceB = RawJson.fromBufferSlice(bufferB, 3, 7)

        assertTrue(sliceA.contentEquals(sliceB))
        assertEquals(sliceA, sliceB)
        assertEquals(sliceA.hashCode(), sliceB.hashCode())
    }

    @Test
    fun differentContentIsNotEqualRegardlessOfLength() {
        val a = RawJson.fromString("""{"a":1}""")
        val b = RawJson.fromString("""{"a":2}""")
        val shorter = RawJson.fromString("""{"a":1""")

        assertFalse(a.contentEquals(b))
        assertFalse(a == b)
        assertFalse(a.contentEquals(shorter))
    }

    @Test
    fun contentEqualsAgainstNullOrOtherTypeIsFalse() {
        val a = RawJson.fromString("42")

        assertFalse(a.contentEquals(null))
        assertFalse(a.equals("42"))
    }

    @Test
    fun sameInstanceIsEqualToItself() {
        val a = RawJson.fromString("""{"a":1}""")

        assertTrue(a.contentEquals(a))
        assertEquals(a, a)
    }
}
