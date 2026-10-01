package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.captureRawJson
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.StreamingGhostSource
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [StreamingGhostSource], the buffering layer behind every streaming [GhostJsonReader].
 * Its window is [com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.STREAMING_BUFFER_SIZE] (8192) bytes, so this file
 * deliberately uses payloads larger than that to exercise the segment-realignment (`getSlow`)
 * and cross-segment continuation branches that smaller payloads elsewhere never reach.
 */
@OptIn(InternalGhostApi::class)
class StreamingGhostSourceTest {

    private fun sourceOf(json: String): StreamingGhostSource =
        StreamingGhostSource(okioSource = Buffer().writeUtf8(json))

    // ── Direct GhostSource contract tests (simple, hand-verifiable semantics) ──────────

    @Test
    fun get_readsBytesWithinFirstSegment() {
        val source = sourceOf(json = "Hello")
        assertEquals(
            expected = 'H'.code,
            actual = source[0]
        )
        assertEquals(
            expected = 'o'.code,
            actual = source[4]
        )
    }

    @Test
    fun get_readsAcrossSegmentBoundary() {
        val payload = "a".repeat(9000)
        val source = sourceOf(json = payload)
        assertEquals(
            expected = 'a'.code,
            actual = source[0]
        )
        // Past STREAMING_BUFFER_SIZE (8192): forces getSlow to realign to a new segment.
        assertEquals(
            expected = 'a'.code,
            actual = source[8500]
        )
        assertEquals(
            expected = 'a'.code,
            actual = source[8999]
        )
    }

    @Test
    fun get_throwsForIndexBeyondAvailableData() {
        val source = sourceOf(json = "short")
        assertFailsWith<IndexOutOfBoundsException> { source[100] }
    }

    @Test
    fun decodeToString_decodesWithinBufferedSegment() {
        val source = sourceOf(json = """{"key":"value"}""")
        source[0] // establishes the buffered segment
        assertEquals(
            expected = "key",
            actual = source.decodeToString(start = 2, end = 5)
        )
    }

    @Test
    fun decodeToString_fallsBackWhenRangeOutsideBufferedSegment() {
        val payload = "a".repeat(9000) + "END"
        val source = sourceOf(json = payload)
        source[0] // buffers [0, 8192) only
        assertEquals(
            expected = "END",
            actual = source.decodeToString(start = 9000, end = 9003)
        )
    }

    @Test
    fun contentEquals_trueForMatchingByteString() {
        val source = sourceOf(json = "hello world")
        assertTrue(actual = source.contentEquals(start = 0, expected = "hello".encodeUtf8()))
    }

    @Test
    fun contentEquals_falseForMismatch() {
        val source = sourceOf(json = "hello world")
        assertFalse(actual = source.contentEquals(start = 0, expected = "world".encodeUtf8()))
    }

    @Test
    fun contentEqualsString_trueForMatch() {
        val source = sourceOf(json = """{"key":"value"}""")
        assertTrue(actual = source.contentEqualsString(start = 2, length = 3, expected = "key"))
    }

    @Test
    fun contentEqualsString_falseForLengthMismatch() {
        val source = sourceOf(json = """{"key":"value"}""")
        assertFalse(actual = source.contentEqualsString(start = 2, length = 4, expected = "key"))
    }

    @Test
    fun contentEqualsString_falseForContentMismatch() {
        val source = sourceOf(json = """{"key":"value"}""")
        assertFalse(actual = source.contentEqualsString(start = 2, length = 3, expected = "abc"))
    }

    @Test
    fun contentEqualsString_crossesSegmentBoundary() {
        val payload = "a".repeat(9000) + "needle"
        val source = sourceOf(json = payload)
        source[0]
        assertTrue(actual = source.contentEqualsString(start = 9000, length = 6, expected = "needle"))
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
        assertEquals(
            expected = padding,
            actual = reader.nextString()
        )
        reader.consumeArraySeparator()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(
            expected = 777,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    @Test
    fun readsHugeStringValueCrossingSegmentBoundary() {
        val longValue = "y".repeat(9000)
        val json = "{\"v\":\"$longValue\"}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(
            expected = longValue,
            actual = reader.nextString()
        )
        reader.endObject()
    }

    @Test
    fun readsHugeStringValueWithEscapeCrossingSegmentBoundary() {
        val prefix = "y".repeat(9000)
        val json = "{\"v\":\"$prefix\\nend\"}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(
            expected = prefix + "\nend",
            actual = reader.nextString()
        )
        reader.endObject()
    }

    @Test
    fun readsHugeNonAsciiStringValueCrossingSegmentBoundary() {
        val longValue = "漢".repeat(4000) // multi-byte UTF-8, well past the 8192-byte segment
        val json = "{\"v\":\"$longValue\"}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(
            expected = longValue,
            actual = reader.nextString()
        )
        reader.endObject()
    }

    @Test
    fun skipsWhitespaceRunCrossingSegmentBoundary() {
        val padding = " ".repeat(9000)
        val json = "{$padding\"v\":1}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        reader.skipWhitespace()
        assertEquals(
            expected = "v",
            actual = reader.readQuotedString()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    // ── Sliding consume (Okio prefix skip) ─────────────────────────────────────────────

    @Test
    fun releaseBefore_skipsFullWindowsBehindReader() {
        val window = GhostJsonWriterConstants.STREAMING_BUFFER_SIZE
        val touchAt = window * 3 + 1_000
        val payload = "a".repeat(touchAt + window)
        val okio = Buffer().writeUtf8(payload)
        val source = StreamingGhostSource(okioSource = okio)

        // Touch far into the document so Okio has buffered a large prefix.
        assertEquals(
            expected = 'a'.code,
            actual = source[touchAt]
        )
        val sizeBefore = okio.size

        // retainFrom = touchAt - window, aligned down to a window multiple.
        source.releaseBefore(absoluteIndex = touchAt)
        val expectedDiscarded = ((touchAt - window) / window) * window
        assertEquals(
            expected = expectedDiscarded,
            actual = source.discarded
        )
        assertTrue(
            actual = okio.size < sizeBefore,
            message = "Okio buffer should shrink after releaseBefore"
        )
        assertEquals(
            expected = 'a'.code,
            actual = source[touchAt],
            message = "bytes at/after retain window must stay readable"
        )
        assertFailsWith<IndexOutOfBoundsException> {
            source[0]
        }
    }

    @Test
    fun releaseBefore_respectsPin() {
        val window = GhostJsonWriterConstants.STREAMING_BUFFER_SIZE
        val touchAt = window * 4
        val source = StreamingGhostSource(okioSource = Buffer().writeUtf8("a".repeat(touchAt + window)))
        source[touchAt]
        source.pin(absoluteIndex = 100)
        source.releaseBefore(absoluteIndex = touchAt)
        // Pin at 100 blocks aligned retainFrom from advancing past 0.
        assertEquals(
            expected = 0,
            actual = source.discarded
        )
        assertEquals(
            expected = 'a'.code,
            actual = source[100]
        )
        source.unpin()
        source.releaseBefore(absoluteIndex = touchAt)
        assertTrue(actual = source.discarded >= window)
    }

    @Test
    fun reader_slidingConsume_parsesMultiSegmentDocument() {
        // Enough small fields to span several windows (need > window + margin to discard).
        val fieldCount = (GhostJsonWriterConstants.STREAMING_BUFFER_SIZE * 4 / 110) + 50
        val fields = (0 until fieldCount).joinToString(",") { i ->
            val pad = "x".repeat(100)
            "\"f$i\":\"$pad$i\""
        }
        val json = "{$fields}"
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        reader.beginObject()
        for (i in 0 until fieldCount) {
            reader.skipWhitespace()
            assertEquals(
                expected = "f$i",
                actual = reader.readQuotedString()
            )
            reader.consumeKeySeparator()
            assertEquals(
                expected = "x".repeat(100) + "$i",
                actual = reader.nextString()
            )
            if (i < fieldCount - 1) reader.consumeArraySeparator()
        }
        reader.endObject()
        val streaming = reader.source as StreamingGhostSource
        assertTrue(
            actual = streaming.discarded > 0,
            message = "expected sliding consume to discard prefix after parsing ~${json.length} bytes"
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
        assertTrue(actual = raw.asDisplayString().contains(padding))
        reader.consumeArraySeparator()
        reader.skipWhitespace(); reader.readQuotedString(); reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
        reader.endObject()
    }
}
