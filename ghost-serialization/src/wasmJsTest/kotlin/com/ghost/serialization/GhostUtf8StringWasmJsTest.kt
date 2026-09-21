@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the Wasm-only `ghostUtf8BytesToString` actual (raw `TextDecoder` js interop over a
 * cached, growable `Uint8Array`, added for the Safari encode-cliff fix #16) — no other
 * target/test can reach it.
 *
 * Sharp edge: the cache is reused and grown across calls, and decoding only reads
 * `[0, length)` via `subarray`. A stale-buffer or length bug would silently return leftover
 * bytes from a previous call instead of throwing.
 */
class GhostUtf8StringWasmJsTest {

    @Test
    fun decodesEmptyRange() {
        assertEquals("", ghostUtf8BytesToString(ByteArray(0), 0, 0))
        assertEquals("", ghostUtf8BytesToString("hello".encodeToByteArray(), 0, 0))
    }

    @Test
    fun decodesPlainAscii() {
        val bytes = "the quick brown fox".encodeToByteArray()
        assertEquals("the quick brown fox", ghostUtf8BytesToString(bytes, 0, bytes.size))
    }

    @Test
    fun decodesMultiByteUtf8() {
        val text = "héllo wörld 漢字 🔥👻🎉"
        val bytes = text.encodeToByteArray()
        assertEquals(text, ghostUtf8BytesToString(bytes, 0, bytes.size))
    }

    @Test
    fun decodesNonZeroOffsetSubrange() {
        // Real callers always pass offset=0; this exercises the arbitrary-range slicing path directly.
        val prefix = "IGNORE:".encodeToByteArray()
        val payload = "漢字テスト".encodeToByteArray()
        val suffix = ":IGNORE".encodeToByteArray()
        val combined = prefix + payload + suffix
        val decoded = ghostUtf8BytesToString(combined, prefix.size, payload.size)
        assertEquals("漢字テスト", decoded)
    }

    @Test
    fun cachedViewShrinksCorrectlyAfterLargerCall() {
        // Grows the cache past its 4096-byte floor, then decodes something short — wrong length
        // bookkeeping would return leftover bytes from the prior call instead of "hi".
        val large = "x".repeat(10_000).encodeToByteArray()
        assertEquals("x".repeat(10_000), ghostUtf8BytesToString(large, 0, large.size))

        val small = "hi".encodeToByteArray()
        assertEquals("hi", ghostUtf8BytesToString(small, 0, small.size))
    }

    @Test
    fun repeatedCallsWithGrowingLengthsStayCorrect() {
        for (size in intArrayOf(1, 10, 100, 1000, 5000, 50, 2)) {
            val text = "y".repeat(size)
            assertEquals(text, ghostUtf8BytesToString(text.encodeToByteArray(), 0, size))
        }
    }
}
