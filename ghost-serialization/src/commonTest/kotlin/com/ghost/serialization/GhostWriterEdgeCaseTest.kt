@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals


class GhostWriterEdgeCaseTest {

    private fun writerToString(block: (GhostJsonWriter) -> Any?): String {
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        block(writer)
        writer.flush()
        return buffer.readUtf8()
    }

    // ── A. PRIMITIVE OUTPUT ──────────────────────────────────────────

    @Test
    fun writesIntValue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(42).endObject()
        }
        assertEquals(
            expected = "{\"v\":42}",
            actual = json
        )
    }

    @Test
    fun writesLongMaxValue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(Long.MAX_VALUE).endObject()
        }
        assertEquals(
            expected = "{\"v\":${Long.MAX_VALUE}}",
            actual = json
        )
    }

    @Test
    fun writesLongMinValue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(Long.MIN_VALUE).endObject()
        }
        assertEquals(
            expected = "{\"v\":${Long.MIN_VALUE}}",
            actual = json
        )
    }

    @Test
    fun writesDoubleValue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(3.14).endObject()
        }
        assertEquals(
            expected = "{\"v\":3.14}",
            actual = json
        )
    }

    @Test
    fun writesFloatValue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(2.5f).endObject()
        }
        assertEquals(
            expected = "{\"v\":2.5}",
            actual = json
        )
    }

    @Test
    fun writesBooleanTrue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(true).endObject()
        }
        assertEquals(
            expected = "{\"v\":true}",
            actual = json
        )
    }

    @Test
    fun writesBooleanFalse() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(false).endObject()
        }
        assertEquals(
            expected = "{\"v\":false}",
            actual = json
        )
    }

    @Test
    fun writesNull() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").nullValue().endObject()
        }
        assertEquals(
            expected = "{\"v\":null}",
            actual = json
        )
    }

    // ── B. STRING ESCAPING ───────────────────────────────────────────

    @Test
    fun escapesQuotesInString() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("say \"hello\"").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"say \\\"hello\\\"\"}",
            actual = json
        )
    }

    @Test
    fun escapesBackslash() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("path\\to").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"path\\\\to\"}",
            actual = json
        )
    }

    @Test
    fun escapesControlCharacters() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("a\nb\tc\rd").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"a\\nb\\tc\\rd\"}",
            actual = json
        )
    }

    @Test
    fun escapesBackspaceAndFormFeed() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("\b\u000C").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"\\b\\f\"}",
            actual = json
        )
    }

    @Test
    fun writesEmptyString() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"\"}",
            actual = json
        )
    }

    @Test
    fun writesUnicodeDirectly() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("漢字").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"漢字\"}",
            actual = json
        )
    }

    @Test
    fun writesAsciiPrefixThenUnicode() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("hello漢字").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"hello漢字\"}",
            actual = json
        )
    }

    @Test
    fun writesEmojiDirectly() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("🚀🔥").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"🚀🔥\"}",
            actual = json
        )
    }

    // ── C. STRUCTURE ─────────────────────────────────────────────────

    @Test
    fun writesEmptyObject() {
        val json = writerToString { w -> w.beginObject().endObject() }
        assertEquals(
            expected = "{}",
            actual = json
        )
    }

    @Test
    fun writesEmptyArray() {
        val json = writerToString { w -> w.beginArray().endArray() }
        assertEquals(
            expected = "[]",
            actual = json
        )
    }

    @Test
    fun writesArrayWithMultipleValues() {
        val json = writerToString { w ->
            w.beginArray().value(1).value(2).value(3).endArray()
        }
        assertEquals(
            expected = "[1,2,3]",
            actual = json
        )
    }

    @Test
    fun writesNestedObjects() {
        val json = writerToString { w ->
            w.beginObject()
                .name(key = "outer")
                .beginObject()
                .name(key = "inner").value("deep")
                .endObject()
                .endObject()
        }
        assertEquals(
            expected = "{\"outer\":{\"inner\":\"deep\"}}",
            actual = json
        )
    }

    @Test
    fun writesArrayInsideObject() {
        val json = writerToString { w ->
            w.beginObject()
                .name(key = "items")
                .beginArray()
                .value("a")
                .value("b")
                .endArray()
                .endObject()
        }
        assertEquals(
            expected = "{\"items\":[\"a\",\"b\"]}",
            actual = json
        )
    }

    @Test
    fun writesMultipleFieldsWithCommas() {
        val json = writerToString { w ->
            w.beginObject()
                .name(key = "a").value(1)
                .name(key = "b").value(2)
                .name(key = "c").value(3)
                .endObject()
        }
        assertEquals(
            expected = "{\"a\":1,\"b\":2,\"c\":3}",
            actual = json
        )
    }

    // ── D. DEPTH PROTECTION ──────────────────────────────────────────

    @Test
    fun writerRespectsMaxDepth() {
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        assertThrowsGhostException {
            repeat(300) { writer.beginObject().name(key = "a") }
        }
    }

    // ── E. ZERO & NEGATIVE VALUES ────────────────────────────────────

    @Test
    fun writesZeroInt() {
        val json = writerToString { w -> w.beginObject().name(key = "v").value(0).endObject() }
        assertEquals(
            expected = "{\"v\":0}",
            actual = json
        )
    }

    @Test
    fun writesNegativeInt() {
        val json = writerToString { w -> w.beginObject().name(key = "v").value(-999).endObject() }
        assertEquals(
            expected = "{\"v\":-999}",
            actual = json
        )
    }

    @Test
    fun writesNegativeLong() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(-1L).endObject()
        }
        assertEquals(
            expected = "{\"v\":-1}",
            actual = json
        )
    }

    @Test
    fun writesNegativeDouble() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(-0.5).endObject()
        }
        assertEquals(
            expected = "{\"v\":-0.5}",
            actual = json
        )
    }

    @Test
    fun writesZeroDouble() {
        val json = writerToString { w -> w.beginObject().name(key = "v").value(0.0).endObject() }
        assertEquals(
            expected = "{\"v\":0.0}",
            actual = json
        )
    }

    @Test
    fun writesNegativeZeroDouble() {
        val json = writerToString { w -> w.beginObject().name(key = "v").value(-0.0).endObject() }
        assertEquals(
            expected = "{\"v\":-0.0}",
            actual = json
        )
    }

    @Test
    fun writesWithZeroCapacityWriter() {
        val writer = com.ghost.serialization.writer.bytes.FlatByteArrayWriter(initialCapacity = 0)
        writer.writeByte('A'.code)
        assertEquals(
            expected = "A",
            actual = writer.array.decodeToString(0, 1)
        )
    }

    @Test
    fun testPrimitiveCollectionsZeroCapacity() {
        val intList = com.ghost.serialization.serializers.GhostIntList(initialCapacity = 0)
        intList.add(value = 42)
        assertEquals(
            expected = 42,
            actual = intList.toArray()[0]
        )

        val longList = com.ghost.serialization.serializers.GhostLongList(initialCapacity = 0)
        longList.add(value = 99L)
        assertEquals(
            expected = 99L,
            actual = longList.toArray()[0]
        )
    }

    // ── F. COMPLEX STRUCTURES ────────────────────────────────────────

    @Test
    fun writesObjectInsideArray() {
        val json = writerToString { w ->
            w.beginArray()
                .beginObject().name(key = "id").value(1).endObject()
                .beginObject().name(key = "id").value(2).endObject()
                .endArray()
        }
        assertEquals(
            expected = "[{\"id\":1},{\"id\":2}]",
            actual = json
        )
    }

    @Test
    fun writesArrayOfArrays() {
        val json = writerToString { w ->
            w.beginArray()
                .beginArray().value(1).value(2).endArray()
                .beginArray().value(3).value(4).endArray()
                .endArray()
        }
        assertEquals(
            expected = "[[1,2],[3,4]]",
            actual = json
        )
    }

    @Test
    fun writesMultipleNullsInObject() {
        val json = writerToString { w ->
            w.beginObject()
                .name(key = "a").nullValue()
                .name(key = "b").nullValue()
                .name(key = "c").nullValue()
                .endObject()
        }
        assertEquals(
            expected = "{\"a\":null,\"b\":null,\"c\":null}",
            actual = json
        )
    }

    @Test
    fun writesNullInterleavedWithValues() {
        val json = writerToString { w ->
            w.beginObject()
                .name(key = "a").value(1)
                .name(key = "b").nullValue()
                .name(key = "c").value("text")
                .name(key = "d").nullValue()
                .name(key = "e").value(true)
                .endObject()
        }
        assertEquals(
            expected = "{\"a\":1,\"b\":null,\"c\":\"text\",\"d\":null,\"e\":true}",
            actual = json
        )
    }

    @Test
    fun writesDeeplyNestedStructure() {
        val json = writerToString { w ->
            w.beginObject()
                .name(key = "l1").beginObject()
                .name(key = "l2").beginObject()
                .name(key = "l3").beginObject()
                .name(key = "leaf").value("deep")
                .endObject()
                .endObject()
                .endObject()
                .endObject()
        }
        assertEquals(
            expected = "{\"l1\":{\"l2\":{\"l3\":{\"leaf\":\"deep\"}}}}",
            actual = json
        )
    }

    // ── G. FIELD NAME ESCAPING ───────────────────────────────────────

    @Test
    fun escapesQuotesInFieldName() {
        val json = writerToString { w ->
            w.beginObject().name(key = "say\"hi").value(1).endObject()
        }
        assertEquals(
            expected = "{\"say\\\"hi\":1}",
            actual = json
        )
    }

    @Test
    fun escapesBackslashInFieldName() {
        val json = writerToString { w ->
            w.beginObject().name(key = "path\\to").value(1).endObject()
        }
        assertEquals(
            expected = "{\"path\\\\to\":1}",
            actual = json
        )
    }

    @Test
    fun writesLongString() {
        val longStr = "x".repeat(10_000)
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(longStr).endObject()
        }
        assertEquals(
            expected = "{\"v\":\"$longStr\"}",
            actual = json
        )
    }

    private inline fun assertThrowsGhostException(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected GhostJsonException to be thrown")
        } catch (e: GhostJsonException) {
        }
    }
}
