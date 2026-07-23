@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.types.RawJson
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Direct unit tests for [GhostJsonFlatWriter] — the in-memory / [FlatByteArrayWriter]-backed
 * writer used by every KSP-generated serializer's flat encode path. Mirrors
 * [com.ghost.serialization.GhostWriterEdgeCaseTest]'s scenarios (which only exercises the
 * sibling Okio-streaming [GhostJsonWriter]) so both writers get the same direct scrutiny,
 * plus the flat writer's own fused/raw APIs that [GhostJsonWriter] doesn't have.
 */
class GhostFlatWriterEdgeCaseTest {

    private fun writerToString(block: (GhostJsonFlatWriter) -> Any?): String {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonFlatWriter(byteWriter)
        block(writer)
        return byteWriter.toStringUtf8()
    }

    // ── A. PRIMITIVE OUTPUT ──────────────────────────────────────────

    @Test
    fun writesSingleDigitPositiveInt() {
        assertEquals("""{"v":7}""", writerToString { w -> w.beginObject().name("v").value(7).endObject() })
    }

    @Test
    fun writesSingleDigitNegativeInt() {
        assertEquals("""{"v":-7}""", writerToString { w -> w.beginObject().name("v").value(-7).endObject() })
    }

    @Test
    fun writesMultiDigitInt() {
        assertEquals("""{"v":12345}""", writerToString { w -> w.beginObject().name("v").value(12345).endObject() })
    }

    @Test
    fun writesIntMinValue() {
        assertEquals(
            """{"v":${Int.MIN_VALUE}}""",
            writerToString { w -> w.beginObject().name("v").value(Int.MIN_VALUE).endObject() }
        )
    }

    @Test
    fun writesLongMaxValue() {
        assertEquals(
            """{"v":${Long.MAX_VALUE}}""",
            writerToString { w -> w.beginObject().name("v").value(Long.MAX_VALUE).endObject() }
        )
    }

    @Test
    fun writesLongMinValue() {
        assertEquals(
            """{"v":${Long.MIN_VALUE}}""",
            writerToString { w -> w.beginObject().name("v").value(Long.MIN_VALUE).endObject() }
        )
    }

    @Test
    fun writesIntMinValueAsLong() {
        assertEquals(
            """{"v":${Int.MIN_VALUE}}""",
            writerToString { w -> w.beginObject().name("v").value(Int.MIN_VALUE.toLong()).endObject() }
        )
    }

    @Test
    fun writesDoubleValue() {
        assertEquals("""{"v":3.14}""", writerToString { w -> w.beginObject().name("v").value(3.14).endObject() })
    }

    @Test
    fun writesWholeNumberDouble() {
        assertEquals("""{"v":5.0}""", writerToString { w -> w.beginObject().name("v").value(5.0).endObject() })
    }

    @Test
    fun writesLargeDoubleBeyondSafeIntegerRange() {
        assertEquals("""{"v":1.0E20}""", writerToString { w -> w.beginObject().name("v").value(1e20).endObject() })
    }

    @Test
    fun writesNegativeZeroDouble() {
        assertEquals("""{"v":-0.0}""", writerToString { w -> w.beginObject().name("v").value(-0.0).endObject() })
    }

    @Test
    fun writesFloatValue() {
        assertEquals("""{"v":2.5}""", writerToString { w -> w.beginObject().name("v").value(2.5f).endObject() })
    }

    @Test
    fun writesWholeNumberFloat() {
        assertEquals("""{"v":4.0}""", writerToString { w -> w.beginObject().name("v").value(4.0f).endObject() })
    }

    @Test
    fun writesNegativeZeroFloat() {
        assertEquals("""{"v":-0.0}""", writerToString { w -> w.beginObject().name("v").value(-0.0f).endObject() })
    }

    @Test
    fun doubleValueThrowsGhostExceptionForNaN() {
        assertFailsWith<GhostJsonException> {
            writerToString { w -> w.beginObject().name("v").value(Double.NaN).endObject() }
        }
    }

    @Test
    fun doubleValueThrowsGhostExceptionForInfinity() {
        assertFailsWith<GhostJsonException> {
            writerToString { w -> w.beginObject().name("v").value(Double.POSITIVE_INFINITY).endObject() }
        }
    }

    @Test
    fun floatValueThrowsGhostExceptionForNaN() {
        assertFailsWith<GhostJsonException> {
            writerToString { w -> w.beginObject().name("v").value(Float.NaN).endObject() }
        }
    }

    @Test
    fun writesBooleanTrue() {
        assertEquals("""{"v":true}""", writerToString { w -> w.beginObject().name("v").value(true).endObject() })
    }

    @Test
    fun writesBooleanFalse() {
        assertEquals("""{"v":false}""", writerToString { w -> w.beginObject().name("v").value(false).endObject() })
    }

    @Test
    fun writesNull() {
        assertEquals("""{"v":null}""", writerToString { w -> w.beginObject().name("v").nullValue().endObject() })
    }

    @Test
    fun writesCharValue() {
        assertEquals("""{"v":"x"}""", writerToString { w -> w.beginObject().name("v").value('x').endObject() })
    }

    // ── B. STRING ESCAPING ───────────────────────────────────────────

    @Test
    fun writesEmptyString() {
        assertEquals("""{"v":""}""", writerToString { w -> w.beginObject().name("v").value("").endObject() })
    }

    @Test
    fun escapesQuotesInString() {
        assertEquals(
            "{\"v\":\"say \\\"hello\\\"\"}",
            writerToString { w -> w.beginObject().name("v").value("say \"hello\"").endObject() }
        )
    }

    @Test
    fun escapesBackslash() {
        assertEquals(
            "{\"v\":\"path\\\\to\"}",
            writerToString { w -> w.beginObject().name("v").value("path\\to").endObject() }
        )
    }

    @Test
    fun escapesControlCharacters() {
        assertEquals(
            "{\"v\":\"a\\nb\\tc\\rd\"}",
            writerToString { w -> w.beginObject().name("v").value("a\nb\tc\rd").endObject() }
        )
    }

    @Test
    fun escapesBackspaceAndFormFeed() {
        assertEquals(
            "{\"v\":\"\\b\\f\"}",
            writerToString { w -> w.beginObject().name("v").value("\b").endObject() }
        )
    }

    @Test
    fun writesUnicodeDirectly() {
        assertEquals("""{"v":"漢字"}""", writerToString { w -> w.beginObject().name("v").value("漢字").endObject() })
    }

    @Test
    fun writesEmojiSurrogatePairDirectly() {
        assertEquals("""{"v":"🚀🔥"}""", writerToString { w -> w.beginObject().name("v").value("🚀🔥").endObject() })
    }

    @Test
    fun writesLongPlainAsciiStringPastScratchCapacity() {
        // > WRITER_SCRATCH_SIZE (512) with no escaping: forces writeStringValueRawSlow's
        // "too big for scratch" branch and writeEscaped's "remaining > scratchSize" branch.
        val longStr = "a".repeat(600)
        assertEquals(
            """{"v":"$longStr"}""",
            writerToString { w -> w.beginObject().name("v").value(longStr).endObject() }
        )
    }

    @Test
    fun writesLongStringNeedingEscapesPastScratchCapacity() {
        val longStr = "a".repeat(600) + "\"quoted\""
        val expected = "a".repeat(600) + "\\\"quoted\\\""
        assertEquals(
            "{\"v\":\"$expected\"}",
            writerToString { w -> w.beginObject().name("v").value(longStr).endObject() }
        )
    }

    @Test
    fun writesShortStringNeedingEscapeWithinScratchCapacity() {
        // Short + escape-needing: writeStringValueRawSlow's "fits in scratch" branch
        // (writeEscapedIntoScratch), distinct from the plain-ASCII fast path.
        assertEquals(
            "{\"v\":\"a\\\"b\"}",
            writerToString { w -> w.beginObject().name("v").value("a\"b").endObject() }
        )
    }

    // ── C. STRUCTURE ─────────────────────────────────────────────────

    @Test
    fun writesEmptyObject() {
        assertEquals("{}", writerToString { w -> w.beginObject().endObject() })
    }

    @Test
    fun writesEmptyArray() {
        assertEquals("[]", writerToString { w -> w.beginArray().endArray() })
    }

    @Test
    fun writesArrayWithMultipleValues() {
        assertEquals("[1,2,3]", writerToString { w -> w.beginArray().value(1).value(2).value(3).endArray() })
    }

    @Test
    fun writesNestedObjects() {
        assertEquals(
            """{"outer":{"inner":"deep"}}""",
            writerToString { w ->
                w.beginObject().name("outer").beginObject().name("inner").value("deep").endObject().endObject()
            }
        )
    }

    @Test
    fun writesMultipleFieldsWithCommas() {
        assertEquals(
            """{"a":1,"b":2,"c":3}""",
            writerToString { w ->
                w.beginObject().name("a").value(1).name("b").value(2).name("c").value(3).endObject()
            }
        )
    }

    // ── D. DEPTH PROTECTION ──────────────────────────────────────────

    @Test
    fun writerRespectsMaxDepth() {
        assertFailsWith<GhostJsonException> {
            val byteWriter = FlatByteArrayWriter()
            val writer = GhostJsonFlatWriter(byteWriter)
            repeat(300) { writer.beginObject().name("a") }
        }
    }

    // ── E. RAW VALUE / RAW NAME (flat-writer-only APIs) ───────────────

    @Test
    fun writesRawValueBytes() {
        assertEquals(
            """{"v":{"nested":1}}""",
            writerToString { w ->
                w.beginObject().name("v").rawValue("""{"nested":1}""".encodeToByteArray()).endObject()
            }
        )
    }

    @Test
    fun writesRawValueBytesSlice() {
        val padded = "XX{\"nested\":1}YY".encodeToByteArray()
        assertEquals(
            """{"v":{"nested":1}}""",
            writerToString { w -> w.beginObject().name("v").rawValue(padded, 2, 12).endObject() }
        )
    }

    @Test
    fun writesRawValueFromRawJson() {
        val raw = RawJson.fromString("""{"nested":2}""")
        assertEquals(
            """{"v":{"nested":2}}""",
            writerToString { w -> w.beginObject().name("v").rawValue(raw).endObject() }
        )
    }

    @Test
    fun writesPreEncodedByteStringFieldName() {
        val header = "\"id\":".encodeUtf8()
        assertEquals(
            """{"id":1}""",
            writerToString { w -> w.beginObject().name(header).value(1).endObject() }
        )
    }

    @Test
    fun writeNameRawDelegatesToByteStringName() {
        val header = "\"id\":".encodeUtf8()
        assertEquals(
            """{"id":1}""",
            writerToString { w -> w.beginObject().writeNameRaw(header).value(1).endObject() }
        )
    }

    // ── F. FUSED writeField(header, value) OVERLOADS ──────────────────

    @Test
    fun writeFieldFusesNameAndIntValue() {
        val header = "\"id\":".encodeUtf8()
        assertEquals(
            """{"id":42}""",
            writerToString { w -> w.beginObject().writeField(header, 42).endObject() }
        )
    }

    @Test
    fun writeFieldFusesNameAndLongValue() {
        val header = "\"id\":".encodeUtf8()
        assertEquals(
            """{"id":${Long.MAX_VALUE}}""",
            writerToString { w -> w.beginObject().writeField(header, Long.MAX_VALUE).endObject() }
        )
    }

    @Test
    fun writeFieldFusesNameAndStringValue() {
        val header = "\"name\":".encodeUtf8()
        assertEquals(
            """{"name":"ghost"}""",
            writerToString { w -> w.beginObject().writeField(header, "ghost").endObject() }
        )
    }

    @Test
    fun writeFieldFusesNameAndBooleanValue() {
        val header = "\"active\":".encodeUtf8()
        assertEquals(
            """{"active":true}""",
            writerToString { w -> w.beginObject().writeField(header, true).endObject() }
        )
    }

    @Test
    fun writeFieldFusesNameAndDoubleValue() {
        val header = "\"score\":".encodeUtf8()
        assertEquals(
            """{"score":3.5}""",
            writerToString { w -> w.beginObject().writeField(header, 3.5).endObject() }
        )
    }

    @Test
    fun writeFieldFusesNameAndFloatValue() {
        val header = "\"score\":".encodeUtf8()
        assertEquals(
            """{"score":1.5}""",
            writerToString { w -> w.beginObject().writeField(header, 1.5f).endObject() }
        )
    }

    // ── G. RESET / REUSE ───────────────────────────────────────────────

    @Test
    fun resetAllowsWriterReuseAfterBufferReset() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonFlatWriter(byteWriter)

        writer.beginObject().name("a").value(1).endObject()
        assertEquals("""{"a":1}""", byteWriter.toStringUtf8())

        writer.reset()
        byteWriter.reset()
        writer.beginObject().name("b").value(2).endObject()
        assertEquals("""{"b":2}""", byteWriter.toStringUtf8())
    }
}
