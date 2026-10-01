@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.types.RawJson
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Direct unit tests for [GhostJsonWriter] — the in-memory / [FlatByteArrayWriter]-backed
 * writer used by every KSP-generated serializer's flat encode path. Exercises the same scenarios
 * as `GhostWriterEdgeCaseTest` (which covers the sibling Okio-streaming
 * [GhostJsonWriter]) so both writers receive equivalent coverage, plus the flat writer's
 * fused/raw APIs that [GhostJsonWriter] does not expose.
 */
class GhostFlatWriterEdgeCaseTest {

    private fun writerToString(block: (GhostJsonWriter) -> Any?): String {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonWriter(byteWriter)
        block(writer)
        return byteWriter.toStringUtf8()
    }

    // ── A. PRIMITIVE OUTPUT ──────────────────────────────────────────

    @Test
    fun writesSingleDigitPositiveInt() {
        assertEquals(
            expected = """{"v":7}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(7).endObject() }
        )
    }

    @Test
    fun writesSingleDigitNegativeInt() {
        assertEquals(
            expected = """{"v":-7}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(-7).endObject() }
        )
    }

    @Test
    fun writesMultiDigitInt() {
        assertEquals(
            expected = """{"v":12345}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(12345).endObject() }
        )
    }

    @Test
    fun writesIntMinValue() {
        assertEquals(
            expected = """{"v":${Int.MIN_VALUE}}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(Int.MIN_VALUE).endObject() }
        )
    }

    @Test
    fun writesLongMaxValue() {
        assertEquals(
            expected = """{"v":${Long.MAX_VALUE}}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(Long.MAX_VALUE).endObject() }
        )
    }

    @Test
    fun writesLongMinValue() {
        assertEquals(
            expected = """{"v":${Long.MIN_VALUE}}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(Long.MIN_VALUE).endObject() }
        )
    }

    @Test
    fun writesIntMinValueAsLong() {
        assertEquals(
            expected = """{"v":${Int.MIN_VALUE}}""",
            actual = writerToString { w ->
                w.beginObject().name(key = "v").value(Int.MIN_VALUE.toLong()).endObject()
            }
        )
    }

    @Test
    fun writesDoubleValue() {
        assertEquals(
            expected = """{"v":3.14}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(3.14).endObject() }
        )
    }

    @Test
    fun writesWholeNumberDouble() {
        assertEquals(
            expected = """{"v":5.0}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(5.0).endObject() }
        )
    }

    @Test
    fun writesLargeDoubleBeyondSafeIntegerRange() {
        val json = writerToString { w -> w.beginObject().name(key = "v").value(1e20).endObject() }
        assertEquals(
            expected = """{"v":""",
            actual = json.substring(0, 5)
        )
        assertEquals(
            expected = '}',
            actual = json.last()
        )
        assertEquals(
            expected = 1e20,
            actual = json.substring(5, json.lastIndex).toDouble()
        )
    }

    @Test
    fun writesNegativeZeroDouble() {
        assertEquals(
            expected = """{"v":-0.0}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(-0.0).endObject() }
        )
    }

    @Test
    fun writesFloatValue() {
        assertEquals(
            expected = """{"v":2.5}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(2.5f).endObject() }
        )
    }

    @Test
    fun writesWholeNumberFloat() {
        assertEquals(
            expected = """{"v":4.0}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(4.0f).endObject() }
        )
    }

    @Test
    fun writesNegativeZeroFloat() {
        assertEquals(
            expected = """{"v":-0.0}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(-0.0f).endObject() }
        )
    }

    @Test
    fun doubleValueThrowsGhostExceptionForNaN() {
        assertFailsWith<GhostJsonException> {
            writerToString { w -> w.beginObject().name(key = "v").value(Double.NaN).endObject() }
        }
    }

    @Test
    fun doubleValueThrowsGhostExceptionForInfinity() {
        assertFailsWith<GhostJsonException> {
            writerToString { w ->
                w.beginObject().name(key = "v").value(Double.POSITIVE_INFINITY).endObject()
            }
        }
    }

    @Test
    fun floatValueThrowsGhostExceptionForNaN() {
        assertFailsWith<GhostJsonException> {
            writerToString { w -> w.beginObject().name(key = "v").value(Float.NaN).endObject() }
        }
    }

    @Test
    fun writesBooleanTrue() {
        assertEquals(
            expected = """{"v":true}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(true).endObject() }
        )
    }

    @Test
    fun writesBooleanFalse() {
        assertEquals(
            expected = """{"v":false}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(false).endObject() }
        )
    }

    @Test
    fun writesNull() {
        assertEquals(
            expected = """{"v":null}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").nullValue().endObject() }
        )
    }

    @Test
    fun writesCharValue() {
        assertEquals(
            expected = """{"v":"x"}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value('x').endObject() }
        )
    }

    // ── B. STRING ESCAPING ───────────────────────────────────────────

    @Test
    fun writesEmptyString() {
        assertEquals(
            expected = """{"v":""}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value("").endObject() }
        )
    }

    @Test
    fun escapesQuotesInString() {
        assertEquals(
            expected = "{\"v\":\"say \\\"hello\\\"\"}",
            actual = writerToString { w -> w.beginObject().name(key = "v").value("say \"hello\"").endObject() }
        )
    }

    @Test
    fun escapesBackslash() {
        assertEquals(
            expected = "{\"v\":\"path\\\\to\"}",
            actual = writerToString { w -> w.beginObject().name(key = "v").value("path\\to").endObject() }
        )
    }

    @Test
    fun escapesControlCharacters() {
        assertEquals(
            expected = "{\"v\":\"a\\nb\\tc\\rd\"}",
            actual = writerToString { w -> w.beginObject().name(key = "v").value("a\nb\tc\rd").endObject() }
        )
    }

    @Test
    fun escapesBackspaceAndFormFeed() {
        assertEquals(
            expected = "{\"v\":\"\\b\\f\"}",
            actual = writerToString { w -> w.beginObject().name(key = "v").value("\u0008\u000C").endObject() }
        )
    }

    @Test
    fun writesUnicodeDirectly() {
        assertEquals(
            expected = """{"v":"漢字"}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value("漢字").endObject() }
        )
    }

    @Test
    fun writesAsciiPrefixThenUnicodeWithoutRescanLoss() {
        // Mixed string: ASCII run then BMP — exercises breakIndex preservation on the byte writer.
        assertEquals(
            expected = """{"v":"hello漢字"}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value("hello漢字").endObject() }
        )
    }

    @Test
    fun writesAsciiPrefixThenEscapedQuote() {
        assertEquals(
            expected = "{\"v\":\"hi\\\"漢字\"}",
            actual = writerToString { w -> w.beginObject().name(key = "v").value("hi\"漢字").endObject() }
        )
    }

    @Test
    fun writesEmojiSurrogatePairDirectly() {
        assertEquals(
            expected = """{"v":"🚀🔥"}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value("🚀🔥").endObject() }
        )
    }

    @Test
    fun writesLongPlainAsciiStringPastScratchCapacity() {
        // > WRITER_SCRATCH_SIZE (512) with no escaping: forces writeStringValueRawSlow's
        // "too big for scratch" branch and writeEscaped's "remaining > scratchSize" branch.
        val longStr = "a".repeat(600)
        assertEquals(
            expected = """{"v":"$longStr"}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(longStr).endObject() }
        )
    }

    @Test
    fun writesLongStringNeedingEscapesPastScratchCapacity() {
        val longStr = "a".repeat(600) + "\"quoted\""
        val expected = "a".repeat(600) + "\\\"quoted\\\""
        assertEquals(
            expected = "{\"v\":\"$expected\"}",
            actual = writerToString { w -> w.beginObject().name(key = "v").value(longStr).endObject() }
        )
    }

    @Test
    fun writesShortStringNeedingEscapeWithinScratchCapacity() {
        // Short + escape-needing: writeStringValueRawSlow's "fits in scratch" branch
        // (writeEscapedIntoScratch), distinct from the plain-ASCII fast path.
        assertEquals(
            expected = "{\"v\":\"a\\\"b\"}",
            actual = writerToString { w -> w.beginObject().name(key = "v").value("a\"b").endObject() }
        )
    }

    // ── C. STRUCTURE ─────────────────────────────────────────────────

    @Test
    fun writesEmptyObject() {
        assertEquals(
            expected = "{}",
            actual = writerToString { w -> w.beginObject().endObject() }
        )
    }

    @Test
    fun writesEmptyArray() {
        assertEquals(
            expected = "[]",
            actual = writerToString { w -> w.beginArray().endArray() }
        )
    }

    @Test
    fun writesArrayWithMultipleValues() {
        assertEquals(
            expected = "[1,2,3]",
            actual = writerToString { w -> w.beginArray().value(1).value(2).value(3).endArray() }
        )
    }

    @Test
    fun writesNestedObjects() {
        assertEquals(
            expected = """{"outer":{"inner":"deep"}}""",
            actual = writerToString { w ->
                w.beginObject().name(key = "outer").beginObject().name(key = "inner").value("deep").endObject()
                    .endObject()
            }
        )
    }

    @Test
    fun writesMultipleFieldsWithCommas() {
        assertEquals(
            expected = """{"a":1,"b":2,"c":3}""",
            actual = writerToString { w ->
                w.beginObject().name(key = "a").value(1).name(key = "b").value(2).name(key = "c").value(3).endObject()
            }
        )
    }

    // ── D. DEPTH PROTECTION ──────────────────────────────────────────

    @Test
    fun writerRespectsMaxDepth() {
        assertFailsWith<GhostJsonException> {
            val byteWriter = FlatByteArrayWriter()
            val writer = GhostJsonWriter(byteWriter)
            repeat(300) { writer.beginObject().name(key = "a") }
        }
    }

    // ── E. RAW VALUE / RAW NAME (flat-writer-only APIs) ───────────────

    @Test
    fun writesRawValueBytes() {
        assertEquals(
            expected = """{"v":{"nested":1}}""",
            actual = writerToString { w ->
                w.beginObject().name(key = "v").rawValue("""{"nested":1}""".encodeToByteArray())
                    .endObject()
            }
        )
    }

    @Test
    fun writesRawValueBytesSlice() {
        val padded = "XX{\"nested\":1}YY".encodeToByteArray()
        assertEquals(
            expected = """{"v":{"nested":1}}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").rawValue(
                bytes = padded,
                offset = 2,
                length = 12
            ).endObject() }
        )
    }

    @Test
    fun writesRawValueFromRawJson() {
        val raw = RawJson.fromString(json = """{"nested":2}""")
        assertEquals(
            expected = """{"v":{"nested":2}}""",
            actual = writerToString { w -> w.beginObject().name(key = "v").rawValue(raw).endObject() }
        )
    }

    @Test
    fun writesPreEncodedByteStringFieldName() {
        val header = "\"id\":".encodeUtf8()
        assertEquals(
            expected = """{"id":1}""",
            actual = writerToString { w -> w.beginObject().name(key = header).value(1).endObject() }
        )
    }

    @Test
    fun writeNameRawDelegatesToByteStringName() {
        val header = "\"id\":".encodeUtf8()
        assertEquals(
            expected = """{"id":1}""",
            actual = writerToString { w -> w.beginObject().writeNameRaw(header = header).value(1).endObject() }
        )
    }

    // ── F. FUSED writeField(header, value) OVERLOADS ──────────────────

    @Test
    fun writeFieldFusesNameAndIntValue() {
        val header = "\"id\":".encodeUtf8()
        assertEquals(
            expected = """{"id":42}""",
            actual = writerToString { w -> w.beginObject().writeField(header = header, value = 42).endObject() }
        )
    }

    @Test
    fun writeFieldFusesNameAndLongValue() {
        val header = "\"id\":".encodeUtf8()
        assertEquals(
            expected = """{"id":${Long.MAX_VALUE}}""",
            actual = writerToString { w -> w.beginObject().writeField(
                header = header,
                value = Long.MAX_VALUE
            ).endObject() }
        )
    }

    @Test
    fun writeFieldFusesNameAndStringValue() {
        val header = "\"name\":".encodeUtf8()
        assertEquals(
            expected = """{"name":"ghost"}""",
            actual = writerToString { w -> w.beginObject().writeField(header = header, value = "ghost").endObject() }
        )
    }

    @Test
    fun writeFieldFusesNameAndBooleanValue() {
        val header = "\"active\":".encodeUtf8()
        assertEquals(
            expected = """{"active":true}""",
            actual = writerToString { w -> w.beginObject().writeField(header = header, value = true).endObject() }
        )
    }

    @Test
    fun writeFieldFusesNameAndDoubleValue() {
        val header = "\"score\":".encodeUtf8()
        assertEquals(
            expected = """{"score":3.5}""",
            actual = writerToString { w -> w.beginObject().writeField(header = header, value = 3.5).endObject() }
        )
    }

    @Test
    fun writeFieldFusesNameAndFloatValue() {
        val header = "\"score\":".encodeUtf8()
        assertEquals(
            expected = """{"score":1.5}""",
            actual = writerToString { w -> w.beginObject().writeField(header = header, value = 1.5f).endObject() }
        )
    }

    // ── G. RESET / REUSE ───────────────────────────────────────────────

    @Test
    fun resetAllowsWriterReuseAfterBufferReset() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonWriter(byteWriter)

        writer.beginObject().name(key = "a").value(1).endObject()
        assertEquals(
            expected = """{"a":1}""",
            actual = byteWriter.toStringUtf8()
        )

        writer.reset()
        byteWriter.reset()
        writer.beginObject().name(key = "b").value(2).endObject()
        assertEquals(
            expected = """{"b":2}""",
            actual = byteWriter.toStringUtf8()
        )
    }
}
