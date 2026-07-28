@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.yaml.exception.GhostYamlException
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.strings.beginObject

/**
 * Direct unit tests for [GhostYamlFlatWriter], covering the same scenarios as
 * [com.ghost.serialization.writer.bytes.GhostFlatWriterEdgeCaseTest].
 */
class GhostYamlFlatWriterEdgeCaseTest {

    private fun writerToString(block: (GhostYamlFlatWriter) -> Any?): String {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostYamlFlatWriter(byteWriter)
        block(writer)
        return byteWriter.toStringUtf8()
    }

    private fun roundTripScalar(key: String, write: (GhostYamlFlatWriter) -> Unit): Any? {
        val yaml = writerToString { w ->
            w.beginObject()
            w.name(key)
            write(w)
            w.endObject()
        }
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        return (reader.readDocument() as Map<*, *>) [key]
    }

    // ── A. PRIMITIVE OUTPUT ──────────────────────────────────────────

    @Test
    fun writesSingleDigitPositiveInt() {
        assertEquals("v: 7", writerToString { w -> w.beginObject().name("v").value(7).endObject() }.trim())
    }

    @Test
    fun writesSingleDigitNegativeInt() {
        assertEquals("v: -7", writerToString { w -> w.beginObject().name("v").value(-7).endObject() }.trim())
    }

    @Test
    fun writesMultiDigitInt() {
        assertEquals("v: 12345", writerToString { w -> w.beginObject().name("v").value(12345).endObject() }.trim())
    }

    @Test
    fun writesIntMinValue() {
        assertEquals(
            "v: ${Int.MIN_VALUE}",
            writerToString { w -> w.beginObject().name("v").value(Int.MIN_VALUE).endObject() }.trim()
        )
    }

    @Test
    fun writesLongMaxValue() {
        assertEquals(
            "v: ${Long.MAX_VALUE}",
            writerToString { w -> w.beginObject().name("v").value(Long.MAX_VALUE).endObject() }.trim()
        )
    }

    @Test
    fun writesLongMinValue() {
        assertEquals(
            "v: ${Long.MIN_VALUE}",
            writerToString { w -> w.beginObject().name("v").value(Long.MIN_VALUE).endObject() }.trim()
        )
    }

    @Test
    fun writesIntMinValueAsLong() {
        assertEquals(
            "v: ${Int.MIN_VALUE}",
            writerToString { w -> w.beginObject().name("v").value(Int.MIN_VALUE.toLong()).endObject() }.trim()
        )
    }

    @Test
    fun writesDoubleValue() {
        val yaml = writerToString { w -> w.beginObject().name("v").value(3.14).endObject() }
        assertTrue(yaml.contains("3.14"), yaml)
    }

    @Test
    fun writesWholeNumberDouble() {
        val yaml = writerToString { w -> w.beginObject().name("v").value(5.0).endObject() }
        assertTrue(yaml.contains("5.0") || yaml.contains("5"), yaml)
    }

    @Test
    fun writesLargeDoubleBeyondSafeIntegerRange() {
        val yaml = writerToString { w -> w.beginObject().name("v").value(1e20).endObject() }
        val parsed = roundTripScalar("v") { it.value(1e20) }
        assertEquals(1e20, (parsed as Number).toDouble())
    }

    @Test
    fun writesNegativeZeroDouble() {
        val yaml = writerToString { w -> w.beginObject().name("v").value(-0.0).endObject() }
        assertTrue(yaml.contains("-0.0") || yaml.contains("0.0"), yaml)
    }

    @Test
    fun writesFloatValue() {
        val yaml = writerToString { w -> w.beginObject().name("v").value(2.5f).endObject() }
        assertTrue(yaml.contains("2.5"), yaml)
    }

    @Test
    fun writesWholeNumberFloat() {
        val yaml = writerToString { w -> w.beginObject().name("v").value(4.0f).endObject() }
        assertTrue(yaml.contains("4.0") || yaml.contains("4"), yaml)
    }

    @Test
    fun writesNegativeZeroFloat() {
        val yaml = writerToString { w -> w.beginObject().name("v").value(-0.0f).endObject() }
        assertTrue(yaml.contains("-0.0") || yaml.contains("0.0"), yaml)
    }

    @Test
    fun writesNaNAsStringLiteral() {
        val yaml = writerToString { w -> w.beginObject().name("v").value(Double.NaN).endObject() }
        assertTrue(yaml.contains("NaN"), yaml)
    }

    @Test
    fun writesInfinityAsStringLiteral() {
        val yaml = writerToString { w -> w.beginObject().name("v").value(Double.POSITIVE_INFINITY).endObject() }
        assertTrue(yaml.contains("Infinity"), yaml)
    }

    @Test
    fun writesBooleanTrue() {
        assertEquals("v: true", writerToString { w -> w.beginObject().name("v").value(true).endObject() }.trim())
    }

    @Test
    fun writesBooleanFalse() {
        assertEquals("v: false", writerToString { w -> w.beginObject().name("v").value(false).endObject() }.trim())
    }

    @Test
    fun writesNull() {
        assertEquals("v: null", writerToString { w -> w.beginObject().name("v").nullValue().endObject() }.trim())
    }

    @Test
    fun writesCharValue() {
        val yaml = writerToString { w -> w.beginObject().name("v").value('x').endObject() }
        assertTrue(yaml.contains("\"x\""), yaml)
    }

    @Test
    fun writesULongBeyondLongMaxQuoted() {
        val yaml = writerToString { w -> w.beginObject().name("v").value(ULong.MAX_VALUE).endObject() }
        assertTrue(yaml.contains("\"18446744073709551615\""), yaml)
    }

    // ── B. STRING ESCAPING ───────────────────────────────────────────

    @Test
    fun writesEmptyString() {
        val yaml = writerToString { w -> w.beginObject().name("v").value("").endObject() }
        assertTrue(yaml.contains("\"\""), yaml)
    }

    @Test
    fun escapesQuotesInString() {
        val yaml = writerToString { w -> w.beginObject().name("v").value("say \"hello\"").endObject() }
        assertTrue(yaml.contains("\\\"hello\\\""), yaml)
    }

    @Test
    fun escapesBackslash() {
        val yaml = writerToString { w -> w.beginObject().name("v").value("path\\to").endObject() }
        assertTrue(yaml.contains("\\\\"), yaml)
    }

    @Test
    fun escapesControlCharacters() {
        val yaml = writerToString { w -> w.beginObject().name("v").value("a\nb\tc\rd").endObject() }
        assertTrue(yaml.contains("\\n") && yaml.contains("\\t") && yaml.contains("\\r"), yaml)
    }

    @Test
    fun escapesBackspaceAndFormFeed() {
        val yaml = writerToString { w -> w.beginObject().name("v").value("\b\u000C").endObject() }
        assertTrue(yaml.contains("\\b") && yaml.contains("\\f"), yaml)
    }

    @Test
    fun writesUnicodeDirectly() {
        val yaml = writerToString { w -> w.beginObject().name("v").value("漢字").endObject() }
        assertTrue(yaml.contains("漢字"), yaml)
    }

    @Test
    fun writesAsciiPrefixThenUnicodeWithoutRescanLoss() {
        val yaml = writerToString { w -> w.beginObject().name("v").value("hello漢字").endObject() }
        assertTrue(yaml.contains("hello漢字"), yaml)
    }

    @Test
    fun writesAsciiPrefixThenEscapedQuote() {
        val yaml = writerToString { w -> w.beginObject().name("v").value("hi\"漢字").endObject() }
        assertTrue(yaml.contains("hi\\\"漢字"), yaml)
    }

    @Test
    fun writesEmojiSurrogatePairDirectly() {
        val yaml = writerToString { w -> w.beginObject().name("v").value("🚀🔥").endObject() }
        assertTrue(yaml.contains("🚀") && yaml.contains("🔥"), yaml)
    }

    @Test
    fun writesLongPlainAsciiStringPastScratchCapacity() {
        val longStr = "a".repeat(600)
        val yaml = writerToString { w -> w.beginObject().name("v").value(longStr).endObject() }
        assertTrue(yaml.contains(longStr), yaml)
    }

    @Test
    fun writesLongStringNeedingEscapesPastScratchCapacity() {
        val longStr = "a".repeat(600) + "\"quoted\""
        val yaml = writerToString { w -> w.beginObject().name("v").value(longStr).endObject() }
        assertTrue(yaml.contains("\\\"quoted\\\""), yaml)
    }

    @Test
    fun writesShortStringNeedingEscapeWithinScratchCapacity() {
        val yaml = writerToString { w -> w.beginObject().name("v").value("a\"b").endObject() }
        assertTrue(yaml.contains("a\\\"b"), yaml)
    }

    // ── C. STRUCTURE ─────────────────────────────────────────────────

    @Test
    fun writesEmptyObject() {
        // Was "" (a dangling scope with no bytes at all, parsed back as null) until the
        // empty-collection fix — see the "F. EMPTY COLLECTIONS" section below.
        assertEquals("{}", writerToString { w -> w.beginObject().endObject() }.trim())
    }

    @Test
    fun writesEmptyArray() {
        assertEquals("[]", writerToString { w -> w.beginArray().endArray() }.trim())
    }

    @Test
    fun writesArrayWithMultipleValues() {
        val yaml = writerToString { w ->
            w.beginObject().name("items")
            w.beginArray()
            w.value(1).value(2).value(3)
            w.endArray()
            w.endObject()
        }
        assertTrue(yaml.contains("- 1") && yaml.contains("- 2") && yaml.contains("- 3"), yaml)
    }

    @Test
    fun writesNestedObjects() {
        val yaml = writerToString { w ->
            w.beginObject()
            w.name("outer")
            w.beginObject()
            w.name("inner")
            w.value("deep")
            w.endObject()
            w.endObject()
        }
        assertTrue(yaml.contains("outer:") && yaml.contains("inner:") && yaml.contains("deep"), yaml)
    }

    @Test
    fun writesMultipleFieldsWithNewlines() {
        val yaml = writerToString { w ->
            w.beginObject().name("a").value(1).name("b").value(2).name("c").value(3).endObject()
        }
        assertTrue(yaml.contains("a:") && yaml.contains("b:") && yaml.contains("c:"), yaml)
    }

    // ── D. DEPTH PROTECTION ──────────────────────────────────────────

    @Test
    fun writerRespectsMaxDepth() {
        assertFailsWith<GhostYamlException> {
            val byteWriter = FlatByteArrayWriter()
            val writer = GhostYamlFlatWriter(byteWriter)
            repeat(65) { writer.beginObject().name("a") }
        }
    }

    @Test
    fun writerFillsExactlyMaxDepthWithoutThrowing() {
        // The 64th beginObject() (currentDepth 0..63) must succeed and stay in bounds;
        // only the 65th (currentDepth == MAX_DEPTH) should throw. Regression test for the
        // off-by-one that undersized the contexts/itemCounts arrays by one element.
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostYamlFlatWriter(byteWriter)
        repeat(64) { writer.beginObject().name("a") }
        writer.value(1)
    }

    // ── F. EMPTY COLLECTIONS ─────────────────────────────────────────

    @Test
    fun emptyNestedObjectFieldRoundTripsAsEmptyMap() {
        val yaml = writerToString { w ->
            w.beginObject()
            w.name("meta")
            w.beginObject()
            w.endObject()
            w.endObject()
        }
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        val result = reader.readDocument() as Map<*, *>
        assertEquals(emptyMap<String, Any?>(), result["meta"])
    }

    @Test
    fun emptyNestedArrayFieldRoundTripsAsEmptyList() {
        val yaml = writerToString { w ->
            w.beginObject()
            w.name("tags")
            w.beginArray()
            w.endArray()
            w.endObject()
        }
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        val result = reader.readDocument() as Map<*, *>
        assertEquals(emptyList<Any?>(), result["tags"])
    }

    @Test
    fun emptyArrayItemRoundTripsAsEmptyList() {
        val yaml = writerToString { w ->
            w.beginArray()
            w.beginArray()
            w.endArray()
            w.value(1)
            w.endArray()
        }
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        val result = reader.readDocument() as List<*>
        assertEquals(listOf(emptyList<Any?>(), 1L), result)
    }

    @Test
    fun emptyObjectItemRoundTripsAsEmptyMap() {
        val yaml = writerToString { w ->
            w.beginArray()
            w.beginObject()
            w.endObject()
            w.endArray()
        }
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        val result = reader.readDocument() as List<*>
        assertEquals(listOf(emptyMap<String, Any?>()), result)
    }

    @Test
    fun rootEmptyObjectRoundTripsAsEmptyMap() {
        val yaml = writerToString { w -> w.beginObject().endObject() }
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        assertEquals(emptyMap<String, Any?>(), reader.readDocument())
    }

    @Test
    fun rootEmptyArrayRoundTripsAsEmptyList() {
        val yaml = writerToString { w -> w.beginArray().endArray() }
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        assertEquals(emptyList<Any?>(), reader.readDocument())
    }

    @Test
    fun emptyObjectFieldFollowedBySiblingStaysWellFormed() {
        val yaml = writerToString { w ->
            w.beginObject()
            w.name("meta")
            w.beginObject()
            w.endObject()
            w.name("count")
            w.value(2)
            w.endObject()
        }
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        val result = reader.readDocument() as Map<*, *>
        assertEquals(emptyMap<String, Any?>(), result["meta"])
        assertEquals(2L, result["count"])
    }

    @Test
    fun nameOutsideObjectScopeThrows() {
        assertFailsWith<GhostYamlException> {
            writerToString { w -> w.name("orphan").value(1) }
        }
    }

    // ── E. FUSED writeField(header, value) OVERLOADS ──────────────────

    @Test
    fun writeFieldFusesNameAndIntValue() {
        val header = "\"id\":".encodeUtf8()
        val yaml = writerToString { w -> w.beginObject().writeField(header, 42).endObject() }
        assertTrue(yaml.contains("id:") && yaml.contains("42"), yaml)
    }

    @Test
    fun writeFieldFusesNameAndLongValue() {
        val header = "\"id\":".encodeUtf8()
        val yaml = writerToString { w -> w.beginObject().writeField(header, Long.MAX_VALUE).endObject() }
        assertTrue(yaml.contains("${Long.MAX_VALUE}"), yaml)
    }

    @Test
    fun writeFieldFusesNameAndStringValue() {
        val header = "\"name\":".encodeUtf8()
        val yaml = writerToString { w -> w.beginObject().writeField(header, "ghost").endObject() }
        assertTrue(yaml.contains("ghost"), yaml)
    }

    @Test
    fun writeFieldFusesNameAndBooleanValue() {
        val header = "\"active\":".encodeUtf8()
        val yaml = writerToString { w -> w.beginObject().writeField(header, true).endObject() }
        assertTrue(yaml.contains("true"), yaml)
    }

    @Test
    fun writeFieldFusesNameAndDoubleValue() {
        val header = "\"score\":".encodeUtf8()
        val yaml = writerToString { w -> w.beginObject().writeField(header, 3.5).endObject() }
        assertTrue(yaml.contains("3.5"), yaml)
    }

    @Test
    fun writeFieldFusesNameAndFloatValue() {
        val header = "\"score\":".encodeUtf8()
        val yaml = writerToString { w -> w.beginObject().writeField(header, 1.5f).endObject() }
        assertTrue(yaml.contains("1.5"), yaml)
    }

    @Test
    fun writeFieldFusesNameAndULongValue() {
        val header = "\"shard\":".encodeUtf8()
        val yaml = writerToString { w -> w.beginObject().writeField(header, ULong.MAX_VALUE).endObject() }
        assertTrue(yaml.contains("18446744073709551615"), yaml)
    }

    @Test
    fun writeNameRawDelegatesToByteStringName() {
        val header = "\"id\":".encodeUtf8()
        val yaml = writerToString { w -> w.beginObject().writeNameRaw(header).value(1).endObject() }
        assertTrue(yaml.contains("id:") && yaml.contains("1"), yaml)
    }

    // ── F. RESET / REUSE ─────────────────────────────────────────────

    @Test
    fun resetAllowsWriterReuseAfterBufferReset() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostYamlFlatWriter(byteWriter)

        writer.beginObject().name("a").value(1).endObject()
        assertTrue(byteWriter.toStringUtf8().contains("a:"))

        writer.reset()
        byteWriter.reset()
        writer.beginObject().name("b").value(2).endObject()
        val second = byteWriter.toStringUtf8()
        assertTrue(second.contains("b:"))
        assertTrue(!second.contains("a:"), second)
    }
}
