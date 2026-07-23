package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [StreamingGhostSource] backs every [GhostJsonReader] built from an `okio.BufferedSource`
 * (`createSourceBridge` always wraps in it), but almost nothing in the suite constructs a
 * reader that way — [NextCharTest] has the only other direct usage, with a single-character
 * payload. Its internal buffering reads [GhostJsonConstants.STREAMING_BUFFER_SIZE] (8192)
 * bytes at a time, so payloads under that size never exercise the segment-realignment
 * (`getSlow`) or cross-segment continuation branches in the scan methods below — this file
 * specifically uses payloads larger than one segment to reach them.
 */
@OptIn(InternalGhostApi::class)
class StreamingGhostSourceTest {

    private fun sourceOf(json: String): StreamingGhostSource =
        StreamingGhostSource(Buffer().writeUtf8(json))

    // ── Direct GhostSource contract tests (simple, hand-verifiable semantics) ──────────

    @Test
    fun get_readsBytesWithinFirstSegment() {
        val source = sourceOf("Hello")
        assertEquals('H'.code, source[0])
        assertEquals('o'.code, source[4])
    }

    @Test
    fun get_readsAcrossSegmentBoundary() {
        val payload = "a".repeat(9000)
        val source = sourceOf(payload)
        assertEquals('a'.code, source[0])
        // Past STREAMING_BUFFER_SIZE (8192): forces getSlow to realign to a new segment.
        assertEquals('a'.code, source[8500])
        assertEquals('a'.code, source[8999])
    }

    @Test
    fun get_throwsForIndexBeyondAvailableData() {
        val source = sourceOf("short")
        assertFailsWith<IndexOutOfBoundsException> { source[100] }
    }

    @Test
    fun decodeToString_decodesWithinBufferedSegment() {
        val source = sourceOf("""{"key":"value"}""")
        source[0] // establishes the buffered segment
        assertEquals("key", source.decodeToString(2, 5))
    }

    @Test
    fun decodeToString_fallsBackWhenRangeOutsideBufferedSegment() {
        val payload = "a".repeat(9000) + "END"
        val source = sourceOf(payload)
        source[0] // buffers [0, 8192) only
        assertEquals("END", source.decodeToString(9000, 9003))
    }

    @Test
    fun contentEquals_trueForMatchingByteString() {
        val source = sourceOf("hello world")
        assertTrue(source.contentEquals(0, "hello".encodeUtf8()))
    }

    @Test
    fun contentEquals_falseForMismatch() {
        val source = sourceOf("hello world")
        assertFalse(source.contentEquals(0, "world".encodeUtf8()))
    }

    @Test
    fun contentEqualsString_trueForMatch() {
        val source = sourceOf("""{"key":"value"}""")
        assertTrue(source.contentEqualsString(2, 3, "key"))
    }

    @Test
    fun contentEqualsString_falseForLengthMismatch() {
        val source = sourceOf("""{"key":"value"}""")
        assertFalse(source.contentEqualsString(2, 4, "key"))
    }

    @Test
    fun contentEqualsString_falseForContentMismatch() {
        val source = sourceOf("""{"key":"value"}""")
        assertFalse(source.contentEqualsString(2, 3, "abc"))
    }

    @Test
    fun contentEqualsString_crossesSegmentBoundary() {
        val payload = "a".repeat(9000) + "needle"
        val source = sourceOf(payload)
        source[0]
        assertTrue(source.contentEqualsString(9000, 6, "needle"))
    }

    // ── Cross-segment parsing via GhostJsonReader (exercises findNextNonWhitespace/ ──────
    // ── findClosingQuote/scanString's segment-boundary continuation branches) ────────────

    @Test
    fun readsFieldAfterHugeStringValueCrossingSegmentBoundary() {
        val padding = "x".repeat(9000)
        val json = "{\"pad\":\"$padding\",\"v\":777}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(padding, reader.nextString())
        reader.consumeArraySeparator()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(777, reader.nextInt())
        reader.endObject()
    }

    @Test
    fun readsHugeStringValueCrossingSegmentBoundary() {
        val longValue = "y".repeat(9000)
        val json = "{\"v\":\"$longValue\"}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(longValue, reader.nextString())
        reader.endObject()
    }

    @Test
    fun readsHugeStringValueWithEscapeCrossingSegmentBoundary() {
        val prefix = "y".repeat(9000)
        val json = "{\"v\":\"$prefix\\nend\"}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(prefix + "\nend", reader.nextString())
        reader.endObject()
    }

    @Test
    fun readsHugeNonAsciiStringValueCrossingSegmentBoundary() {
        val longValue = "漢".repeat(4000) // multi-byte UTF-8, well past the 8192-byte segment
        val json = "{\"v\":\"$longValue\"}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(longValue, reader.nextString())
        reader.endObject()
    }

    @Test
    fun skipsWhitespaceRunCrossingSegmentBoundary() {
        val padding = " ".repeat(9000)
        val json = "{$padding\"v\":1}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        reader.skipWhitespace()
        assertEquals("v", reader.readQuotedString())
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
        reader.endObject()
    }
}
