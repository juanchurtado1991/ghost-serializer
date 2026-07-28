package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.StreamingGhostSource
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.captureRawJson
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextString
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

    // ── Sliding consume (Okio prefix skip) ─────────────────────────────────────────────

    @Test
    fun releaseBefore_skipsFullWindowsBehindReader() {
        val window = GhostJsonConstants.STREAMING_BUFFER_SIZE
        val touchAt = window * 3 + 1_000
        val payload = "a".repeat(touchAt + window)
        val okio = Buffer().writeUtf8(payload)
        val source = StreamingGhostSource(okio)

        // Touch far into the document so Okio has buffered a large prefix.
        assertEquals('a'.code, source[touchAt])
        val sizeBefore = okio.size

        // retainFrom = touchAt - window, aligned down to a window multiple.
        source.releaseBefore(touchAt)
        val expectedDiscarded = ((touchAt - window) / window) * window
        assertEquals(expectedDiscarded, source.discarded)
        assertTrue(okio.size < sizeBefore, "Okio buffer should shrink after releaseBefore")
        assertEquals('a'.code, source[touchAt], "bytes at/after retain window must stay readable")
        assertFailsWith<IndexOutOfBoundsException> {
            source[0]
        }
    }

    @Test
    fun releaseBefore_respectsPin() {
        val window = GhostJsonConstants.STREAMING_BUFFER_SIZE
        val touchAt = window * 4
        val source = StreamingGhostSource(Buffer().writeUtf8("a".repeat(touchAt + window)))
        source[touchAt]
        source.pin(100)
        source.releaseBefore(touchAt)
        // Pin at 100 blocks aligned retainFrom from advancing past 0.
        assertEquals(0, source.discarded)
        assertEquals('a'.code, source[100])
        source.unpin()
        source.releaseBefore(touchAt)
        assertTrue(source.discarded >= window)
    }

    @Test
    fun reader_slidingConsume_parsesMultiSegmentDocument() {
        // Enough small fields to span several windows (need > window + margin to discard).
        val fieldCount = (GhostJsonConstants.STREAMING_BUFFER_SIZE * 4 / 110) + 50
        val fields = (0 until fieldCount).joinToString(",") { i ->
            val pad = "x".repeat(100)
            "\"f$i\":\"$pad$i\""
        }
        val json = "{$fields}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        for (i in 0 until fieldCount) {
            reader.skipWhitespace()
            assertEquals("f$i", reader.readQuotedString())
            reader.consumeKeySeparator()
            assertEquals("x".repeat(100) + "$i", reader.nextString())
            if (i < fieldCount - 1) reader.consumeArraySeparator()
        }
        reader.endObject()
        val streaming = reader.source as StreamingGhostSource
        assertTrue(
            streaming.discarded > 0,
            "expected sliding consume to discard prefix after parsing ~${json.length} bytes"
        )
    }

    @Test
    fun captureRawJson_streaming_survivesReleaseDuringScan() {
        val padding = "z".repeat(12_000)
        val json = "{\"raw\":{\"inner\":\"$padding\"},\"after\":1}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        val raw = reader.captureRawJson()
        assertTrue(raw.asDisplayString().contains(padding))
        reader.consumeArraySeparator()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
        reader.endObject()
    }
}
