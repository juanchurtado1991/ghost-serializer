@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ghostUtf8BytesToString]'s Wasm `actual` (`GhostUtf8String.wasmJs.kt`) is raw `js(...)`
 * interop with a browser `TextDecoder` over a cached, growable `Uint8Array` view — added for the
 * Safari encode-cliff fix (#16) and, being Wasm-only, untested on every other target/by every
 * other test in the suite (`commonTest` can't reach an `actual` that only exists here).
 *
 * The cache (`acquireUtf8View`) is the sharp edge: it's reused and grown across calls, and
 * `textDecodeUtf8` only decodes `[0, length)` of it via `subarray`. A wrong offset/length or a
 * stale-buffer bug after shrinking back down from a larger call would silently decode leftover
 * bytes from a *previous* call instead of throwing.
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
        // Real callers always pass offset=0, but the actual implementation's Uint8Array view +
        // subarray slicing supports arbitrary ranges — exercise that directly.
        val prefix = "IGNORE:".encodeToByteArray()
        val payload = "漢字テスト".encodeToByteArray()
        val suffix = ":IGNORE".encodeToByteArray()
        val combined = prefix + payload + suffix
        val decoded = ghostUtf8BytesToString(combined, prefix.size, payload.size)
        assertEquals("漢字テスト", decoded)
    }

    @Test
    fun cachedViewShrinksCorrectlyAfterLargerCall() {
        // Force the internal Uint8Array cache to grow past its 4096-byte floor, then immediately
        // decode a much shorter string. If the cache's length bookkeeping were wrong, the second
        // call could return leftover bytes from the first (large) call instead of just "hi".
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
